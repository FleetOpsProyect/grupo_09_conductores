#!/usr/bin/env bash
# Verifies the API Gateway authorizer (fleetops-authorizer) for the Conductores routes (SEC-1, HU-03).
#
# Gets an ID token for each test user from Cognito, calls every route through API Gateway and compares
# the HTTP status with the catalog. Also checks one direct call to the EC2 without a token (internal call).
#
# Required environment variables:
#   API             API Gateway invoke URL, without a trailing slash
#   CLIENT_ID       Cognito app client id used by the authorizer
#   TEST_PASSWORD   password of the test users (override one user with PASS_<user>, e.g. PASS_admin1)
#   EC2_URL         direct service URL, e.g. http://<elastic-ip>:8085 (optional; skips the check if empty)
# Optional:
#   AWS_REGION      defaults to us-east-1
#
# Test users (Cognito groups): admin1 (FLEET_ADMIN), driver1 (DRIVER, custom:driver_id of an existing
# driver), driver_ghost (DRIVER, custom:driver_id that does not exist), driver_noid (DRIVER, no
# custom:driver_id), employee1 (EMPLOYEE), tech1 (MAINTENANCE_TECHNICIAN), manager1 (MANAGEMENT).
#
# Usage: ./scripts/verify-authorizer.sh
# Exit code: 0 when every check passes, 1 otherwise. Tokens and passwords are never printed.

set -uo pipefail

: "${API:?Set API to the API Gateway invoke URL}"
: "${CLIENT_ID:?Set CLIENT_ID to the Cognito app client id}"
: "${TEST_PASSWORD:?Set TEST_PASSWORD to the test users password}"
REGION="${AWS_REGION:-us-east-1}"
EC2_URL="${EC2_URL:-}"
OTHER_ID="11111111-2222-4333-8444-000000000000"   # an id that belongs to no test user

failures=0

token_for() {
  local user="$1" pass_var="PASS_$1"
  aws cognito-idp initiate-auth --region "$REGION" --auth-flow USER_PASSWORD_AUTH \
    --client-id "$CLIENT_ID" \
    --auth-parameters "USERNAME=$user,PASSWORD=${!pass_var:-$TEST_PASSWORD}" \
    --query 'AuthenticationResult.IdToken' --output text
}

# Reads custom:driver_id from the token payload (empty when the claim is missing).
driver_id_of() {
  local payload
  payload=$(printf '%s' "$1" | cut -d. -f2 | tr '_-' '/+')
  while (( ${#payload} % 4 )); do payload+="="; done
  printf '%s' "$payload" | base64 -d 2>/dev/null | grep -o '"custom:driver_id":"[^"]*"' | cut -d'"' -f4
}

status_of() {
  local method="$1" url="$2" token="${3:-}" body=()
  [[ "$method" == POST || "$method" == PUT ]] && body=(-H "Content-Type: application/json" -d '{}')
  if [[ -n "$token" ]]; then
    curl -s -o /dev/null -m 15 -w "%{http_code}" -X "$method" -H "Authorization: Bearer $token" "${body[@]}" "$url"
  else
    curl -s -o /dev/null -m 15 -w "%{http_code}" -X "$method" "${body[@]}" "$url"
  fi
}

# check <label> <method> <path> <expected> [token] [base-url]
# expected is a status code, or "!403" for "anything but 403" (the request passed the authorizer).
check() {
  local label="$1" method="$2" path="$3" expected="$4" token="${5:-}" base="${6:-$API}" got ok
  got=$(status_of "$method" "$base$path" "$token")
  if [[ "$expected" == "!403" ]]; then [[ "$got" != 403 && "$got" != 401 ]] && ok=1 || ok=0
  else [[ "$got" == "$expected" ]] && ok=1 || ok=0; fi
  (( ok )) && mark="yes" || { mark="no"; failures=$((failures + 1)); }
  printf "%-14s %-7s %-66s %-9s %-8s %s\n" "$label" "$method" "$path" "$expected" "$got" "$mark"
}

routes_all_forbidden() {
  local label="$1" token="$2" id="$3"
  check "$label" GET  "/drivers/$id" 403 "$token"
  check "$label" GET  "/drivers" 403 "$token"
  check "$label" POST "/drivers" 403 "$token"
  check "$label" PUT  "/drivers/$id" 403 "$token"
  check "$label" POST "/drivers/$id/medical-release" 403 "$token"
}

echo "Obteniendo tokens de Cognito..."
declare -A TOKEN
for user in admin1 driver1 driver_ghost driver_noid employee1 tech1 manager1; do
  TOKEN[$user]=$(token_for "$user") || { echo "No se pudo obtener el token de $user"; exit 1; }
done
DRIVER1_ID=$(driver_id_of "${TOKEN[driver1]}")
GHOST_ID=$(driver_id_of "${TOKEN[driver_ghost]}")
[[ -n "$DRIVER1_ID" && -n "$GHOST_ID" ]] || { echo "driver1 y driver_ghost necesitan custom:driver_id"; exit 1; }

echo
echo "Verificación del authorizer · $(date '+%Y-%m-%d %H:%M %Z')"
printf "%-14s %-7s %-66s %-9s %-8s %s\n" "USUARIO" "MÉTODO" "RUTA" "ESPERADO" "OBTENIDO" ""
printf '%.0s─' {1..112}; echo

# FLEET_ADMIN: every route passes the authorizer (SEC-1)
check admin1 GET  "/drivers/$DRIVER1_ID" 200 "${TOKEN[admin1]}"
check admin1 GET  "/drivers" 200 "${TOKEN[admin1]}"
check admin1 POST "/drivers" "!403" "${TOKEN[admin1]}"
check admin1 PUT  "/drivers/$DRIVER1_ID" "!403" "${TOKEN[admin1]}"
check admin1 POST "/drivers/$DRIVER1_ID/medical-release" "!403" "${TOKEN[admin1]}"

# DRIVER with its own id (HU-03 c2), another id (c3) and the other routes (HU-01 c8, HU-06 c9)
check driver1 GET  "/drivers/$DRIVER1_ID" 200 "${TOKEN[driver1]}"
check driver1 GET  "/drivers/$OTHER_ID" 403 "${TOKEN[driver1]}"
check driver1 GET  "/drivers" 403 "${TOKEN[driver1]}"
check driver1 POST "/drivers" 403 "${TOKEN[driver1]}"
check driver1 PUT  "/drivers/$DRIVER1_ID" 403 "${TOKEN[driver1]}"
check driver1 POST "/drivers/$DRIVER1_ID/medical-release" 403 "${TOKEN[driver1]}"

# DRIVER whose own id does not exist (HU-03 c7) and DRIVER without custom:driver_id (HU-03 c4)
check driver_ghost GET "/drivers/$GHOST_ID" 404 "${TOKEN[driver_ghost]}"
routes_all_forbidden driver_noid "${TOKEN[driver_noid]}" "$DRIVER1_ID"

# Groups without access to this service
for user in employee1 tech1 manager1; do
  routes_all_forbidden "$user" "${TOKEN[$user]}" "$DRIVER1_ID"
done

# No token (Gateway 401) and a token with a broken signature (403)
check "sin token" GET "/drivers/$DRIVER1_ID" 401
# Change one character in the middle of the signature. The last character is not enough: it carries
# padding bits that the decoder ignores, so changing it can leave the signature bytes unchanged.
ADMIN_TOKEN="${TOKEN[admin1]}"
POS=$(( ${#ADMIN_TOKEN} - 20 ))
[[ "${ADMIN_TOKEN:POS:1}" == A ]] && NEW_CHAR=B || NEW_CHAR=A
TAMPERED="${ADMIN_TOKEN:0:POS}${NEW_CHAR}${ADMIN_TOKEN:POS+1}"
check "token alterado" GET "/drivers/$DRIVER1_ID" 403 "$TAMPERED"

# Internal call straight to the EC2, without the Gateway or a token (HU-02 c4, HU-03 c6)
if [[ -n "$EC2_URL" ]]; then
  check "interna EC2" GET "/drivers/$DRIVER1_ID" 200 "" "$EC2_URL"
fi

printf '%.0s─' {1..112}; echo
if (( failures == 0 )); then echo "Resultado: todas las comprobaciones pasaron"; else echo "Resultado: $failures comprobaciones fallaron ❌"; fi
exit $(( failures > 0 ))
