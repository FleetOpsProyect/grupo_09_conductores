#!/bin/sh
#
# Idempotently creates the SNS/SQS resources used by ms-conductores.
# Runs automatically inside LocalStack from /etc/localstack/init/ready.d/ and can
# also be run from the host: bash scripts/init-localstack.sh
# POSIX sh so it works with the shell available in the LocalStack image.
set -eu

REGION="${AWS_REGION:-us-east-1}"
ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"
ACCOUNT_ID="000000000000"
CONTAINER="${LOCALSTACK_CONTAINER:-conductores_localstack}"
CR="$(printf '\r')"

# ── AWS CLI selection ─────────────────────────────────────────────────────────
# 1. awslocal (inside the container), 2. aws with the LocalStack endpoint,
# 3. awslocal through docker exec (host with only Docker installed).
if command -v awslocal > /dev/null 2>&1; then
  aws_cmd() { awslocal --region "$REGION" "$@"; }
elif command -v aws > /dev/null 2>&1; then
  aws_cmd() { aws --endpoint-url "$ENDPOINT" --region "$REGION" "$@"; }
else
  # MSYS_NO_PATHCONV stops Git Bash on Windows from rewriting arguments.
  aws_cmd() { MSYS_NO_PATHCONV=1 docker exec "$CONTAINER" awslocal --region "$REGION" "$@"; }
fi

# Runs the CLI and strips the trailing CR that Windows tools may add.
aws_out() {
  out="$(aws_cmd "$@")"
  printf '%s\n' "${out%"$CR"}"
}

topic_arn() {
  aws_out sns create-topic --name "$1" --output text --query 'TopicArn'
}

# Path-style queue URL: http://localhost:4566/000000000000/<queue_name>
queue_url() {
  aws_cmd sqs create-queue --queue-name "$1" > /dev/null
  printf '%s/%s/%s\n' "$ENDPOINT" "$ACCOUNT_ID" "$1"
}

queue_arn() {
  aws_out sqs get-queue-attributes --queue-url "$1" \
    --attribute-names QueueArn --output text --query 'Attributes.QueueArn'
}

# ── Driver events topic ───────────────────────────────────────────────────────
DRIVER_TOPIC_ARN="$(topic_arn fleetops_driver_events)"
echo "Topic: $DRIVER_TOPIC_ARN"

# ── DLQ and main queue (redrive to the DLQ after 3 failed receives) ───────────
DLQ_URL="$(queue_url fleetops_driver_dlq)"
DLQ_ARN="$(queue_arn "$DLQ_URL")"
echo "DLQ: $DLQ_URL"

DRIVER_QUEUE_URL="$(queue_url fleetops_driver_queue)"
DRIVER_QUEUE_ARN="$(queue_arn "$DRIVER_QUEUE_URL")"

# Set after creation so re-runs converge instead of failing with QueueAlreadyExists.
REDRIVE_ATTRS="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":3}"}' "$DLQ_ARN")"
aws_cmd sqs set-queue-attributes --queue-url "$DRIVER_QUEUE_URL" --attributes "$REDRIVE_ATTRS"
echo "Queue: $DRIVER_QUEUE_URL (RedrivePolicy -> fleetops_driver_dlq, maxReceiveCount 3)"

# ── Document and incident topics ──────────────────────────────────────────────
DOC_TOPIC_ARN="$(topic_arn fleetops_document_events)"
INCIDENT_TOPIC_ARN="$(topic_arn fleetops_incident_events)"

# ── Subscriptions with filter policy and raw message delivery ─────────────────
# Reuses the existing subscription for the queue, or creates it, and then
# (re)applies the attributes so every run ends in the same state.
subscribe() {
  topic="$1"
  endpoint="$2"
  filter_policy="$3"

  sub_arn="$(aws_out sns list-subscriptions-by-topic --topic-arn "$topic" \
    --output text --query "Subscriptions[?Endpoint=='${endpoint}'].SubscriptionArn | [0]")"

  if [ -n "$sub_arn" ] && [ "$sub_arn" != "None" ]; then
    echo "Subscription already exists: $topic -> $endpoint"
  else
    sub_arn="$(aws_out sns subscribe --topic-arn "$topic" --protocol sqs \
      --notification-endpoint "$endpoint" --return-subscription-arn \
      --output text --query 'SubscriptionArn')"
    echo "Subscription created: $topic -> $endpoint"
  fi

  aws_cmd sns set-subscription-attributes --subscription-arn "$sub_arn" \
    --attribute-name RawMessageDelivery --attribute-value true
  aws_cmd sns set-subscription-attributes --subscription-arn "$sub_arn" \
    --attribute-name FilterPolicy --attribute-value "$filter_policy"
  echo "  FilterPolicy: $filter_policy"
}

DOC_FILTER='{"event_type":["document_expired_event","document_renewed_event"]}'
INCIDENT_FILTER='{"event_type":["severe_incident_reported_event"]}'

subscribe "$DOC_TOPIC_ARN" "$DRIVER_QUEUE_ARN" "$DOC_FILTER"
subscribe "$INCIDENT_TOPIC_ARN" "$DRIVER_QUEUE_ARN" "$INCIDENT_FILTER"

echo "SNS/SQS resources ready in LocalStack."
