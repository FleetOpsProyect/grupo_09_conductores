#!/usr/bin/env bash
#
# Crea de forma idempotente los recursos SNS/SQS que consume ms-conductores.
# Se ejecuta en LocalStack desde /etc/localstack/init/ready.d/.
set -euo pipefail

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

# Escapa las comillas para incrustar una cadena JSON como valor de un atributo.
escape_json() {
  printf '%s' "$1" | sed 's/"/\\"/g'
}

# ── Topic principal de eventos de conductores ───────────────────────────────
DRIVER_TOPIC_ARN="$(topic_arn fleetops_driver_events)"
echo "Topic: $DRIVER_TOPIC_ARN"

# ── Cola principal y DLQ ────────────────────────────────────────────────────
DLQ_URL="$(queue_url fleetops_driver_dlq)"
DLQ_ARN="$(queue_arn "$DLQ_URL")"

DRIVER_QUEUE_URL="$(queue_url fleetops_driver_queue)"
DRIVER_QUEUE_ARN="$(queue_arn "$DRIVER_QUEUE_URL")"

# Redrive policy: los mensajes recibidos 3 veces sin éxito pasan a la DLQ.
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":\"3\"}"
ATTRS_JSON="{\"RedrivePolicy\":\"$(escape_json "$REDRIVE_POLICY")\"}"
awslocal sqs set-queue-attributes --queue-url "$DRIVER_QUEUE_URL" \
  --attributes "$ATTRS_JSON" --region "$REGION"

# ── Topics de documentos e incidentes ───────────────────────────────────────
DOC_TOPIC_ARN="$(topic_arn fleetops_document_events)"
INCIDENT_TOPIC_ARN="$(topic_arn fleetops_incident_events)"

# ── Suscripciones con filter policy y raw message delivery ──────────────────
subscribe() {
  local topic_arn="$1"
  local queue_arn="$2"
  local filter_policy="$3"

  if awslocal sns list-subscriptions-by-topic --topic-arn "$topic_arn" --region "$REGION" \
      --output text --query "Subscriptions[?Endpoint=='${queue_arn}'].SubscriptionArn" | grep -q .; then
    echo "Suscripción ya existe: $topic_arn -> $queue_arn"
    return 0
  fi

  local attrs_json
  attrs_json="{\"RawMessageDelivery\":\"true\",\"FilterPolicy\":\"$(escape_json "$filter_policy")\"}"
  awslocal sns subscribe --topic-arn "$topic_arn" --protocol sqs \
    --notification-endpoint "$queue_arn" --attributes "$attrs_json" --region "$REGION" > /dev/null
  echo "Suscripción creada: $topic_arn -> $queue_arn"
}

subscribe "$DOC_TOPIC_ARN" "$DRIVER_QUEUE_ARN" \
  '{"event_type":["document_expired_event","document_renewed_event"]}'
subscribe "$INCIDENT_TOPIC_ARN" "$DRIVER_QUEUE_ARN" \
  '{"event_type":["severe_incident_reported_event"]}'

echo "Recursos SNS/SQS listos en LocalStack."
