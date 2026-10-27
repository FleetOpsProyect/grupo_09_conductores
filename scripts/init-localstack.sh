#!/bin/sh
#
# Crea de forma idempotente los recursos SNS/SQS que consume ms-conductores.
# Se ejecuta en LocalStack desde /etc/localstack/init/ready.d/.
# POSIX sh: la imagen Community de LocalStack no incluye bash, sed ni grep.
set -eu

REGION="${AWS_REGION:-us-east-1}"

topic_arn() {
  awslocal sns create-topic --name "$1" --region "$REGION" \
    --output text --query 'TopicArn'
}

queue_url() {
  awslocal sqs create-queue --queue-name "$1" --region "$REGION" \
    --output text --query 'QueueUrl'
}

queue_arn() {
  awslocal sqs get-queue-attributes --queue-url "$1" \
    --attribute-names QueueArn --region "$REGION" \
    --output text --query 'Attributes.QueueArn'
}

# ── Topic principal de eventos de conductores ───────────────────────────────
DRIVER_TOPIC_ARN="$(topic_arn fleetops_driver_events)"
echo "Topic: $DRIVER_TOPIC_ARN"

# ── DLQ y cola principal (redrive hacia la DLQ tras 3 intentos fallidos) ────
DLQ_URL="$(queue_url fleetops_driver_dlq)"
DLQ_ARN="$(queue_arn "$DLQ_URL")"

DRIVER_QUEUE_ATTRS="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' "$DLQ_ARN")"
DRIVER_QUEUE_URL="$(awslocal sqs create-queue --queue-name fleetops_driver_queue \
  --attributes "$DRIVER_QUEUE_ATTRS" --region "$REGION" --output text --query 'QueueUrl')"
DRIVER_QUEUE_ARN="$(queue_arn "$DRIVER_QUEUE_URL")"

# ── Topics de documentos e incidentes ───────────────────────────────────────
DOC_TOPIC_ARN="$(topic_arn fleetops_document_events)"
INCIDENT_TOPIC_ARN="$(topic_arn fleetops_incident_events)"

# ── Suscripciones con filter policy y raw message delivery ──────────────────
subscribe() {
  topic_arn="$1"
  queue_arn="$2"
  filter_policy="$3"

  subs="$(awslocal sns list-subscriptions-by-topic --topic-arn "$topic_arn" --region "$REGION" \
    --output text --query "Subscriptions[?Endpoint=='${queue_arn}'].SubscriptionArn")"
  if [ -n "$subs" ]; then
    echo "Suscripción ya existe: $topic_arn -> $queue_arn"
    return 0
  fi

  attrs="$(printf '{"RawMessageDelivery":"true","FilterPolicy":"%s"}' "$filter_policy")"
  awslocal sns subscribe --topic-arn "$topic_arn" --protocol sqs \
    --notification-endpoint "$queue_arn" --attributes "$attrs" --region "$REGION" > /dev/null
  echo "Suscripción creada: $topic_arn -> $queue_arn"
}

# El filter policy viaja como cadena JSON ya escapada (valor del atributo FilterPolicy).
DOC_FILTER='{\"event_type\":[\"document_expired_event\",\"document_renewed_event\"]}'
INCIDENT_FILTER='{\"event_type\":[\"severe_incident_reported_event\"]}'

subscribe "$DOC_TOPIC_ARN" "$DRIVER_QUEUE_ARN" "$DOC_FILTER"
subscribe "$INCIDENT_TOPIC_ARN" "$DRIVER_QUEUE_ARN" "$INCIDENT_FILTER"

echo "Recursos SNS/SQS listos en LocalStack."

