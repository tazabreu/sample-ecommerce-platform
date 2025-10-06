#!/usr/bin/env bash
set -euo pipefail

#############################################
# Quickstart validation workflow
# - Creates catalog seed data (category + product)
# - Exercises cart and checkout flow end-to-end
# - Verifies downstream order processing status
# - Cleans up created artefacts
#############################################

CUSTOMER_BASE="${CUSTOMER_BASE:-http://localhost:8080}"
ORDER_BASE="${ORDER_BASE:-http://localhost:8081}"
TARGET_LABEL="local"
POLL_ATTEMPTS=12
POLL_SLEEP_SECONDS=5

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--customer-base URL] [--order-base URL] [--target LABEL]

Options:
  --customer-base URL   Base URL for customer-facing-service (default: $CUSTOMER_BASE)
  --order-base URL      Base URL for order-management-service (default: $ORDER_BASE)
  --target LABEL        Identifier used in logs (default: local)
USAGE
}

log() {
  printf '[%s] %s\n' "$(date -u '+%H:%M:%SZ')" "$1"
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required dependency: $1"
}

uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen
  else
    python - <<'PY'
import uuid
print(uuid.uuid4())
PY
  fi
}

# Parse command-line options
while [[ $# -gt 0 ]]; do
  case "$1" in
    --customer-base)
      CUSTOMER_BASE="$2"
      shift 2
      ;;
    --order-base)
      ORDER_BASE="$2"
      shift 2
      ;;
    --target)
      TARGET_LABEL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "Unknown argument: $1"
      ;;
  esac
done

require curl
require jq

CORRELATION_ID="qs-$TARGET_LABEL-$(uuid | tr '[:upper:]' '[:lower:]')"
SESSION_ID="session-$(uuid | cut -d'-' -f1)"
IDEMPOTENCY_KEY="idemp-$(uuid)"
CATEGORY_ID=""
PRODUCT_ID=""
MANAGER_TOKEN=""
ORDER_NUMBER=""

cleanup() {
  set +e
  if [[ -n "$PRODUCT_ID" && -n "$MANAGER_TOKEN" ]]; then
    curl --silent --show-error --request DELETE \
      --header "Authorization: Bearer $MANAGER_TOKEN" \
      --header "X-Correlation-ID: $CORRELATION_ID" \
      "$CUSTOMER_BASE/api/v1/products/$PRODUCT_ID" >/dev/null 2>&1
  fi
  if [[ -n "$CATEGORY_ID" && -n "$MANAGER_TOKEN" ]]; then
    curl --silent --show-error --request DELETE \
      --header "Authorization: Bearer $MANAGER_TOKEN" \
      --header "X-Correlation-ID: $CORRELATION_ID" \
      "$CUSTOMER_BASE/api/v1/categories/$CATEGORY_ID" >/dev/null 2>&1
  fi
  curl --silent --show-error --request DELETE \
    --header "X-Correlation-ID: $CORRELATION_ID" \
    "$CUSTOMER_BASE/api/v1/carts/$SESSION_ID" >/dev/null 2>&1
  set -e
}

trap cleanup EXIT

log "Target environment: $TARGET_LABEL"
log "Customer service base URL: $CUSTOMER_BASE"
log "Order service base URL: $ORDER_BASE"
log "Correlation ID: $CORRELATION_ID"

log "Obtaining manager JWT token"
LOGIN_PAYLOAD=$(jq -n '{username: "manager", password: "manager123"}')
LOGIN_RESPONSE=$(curl --silent --show-error --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --header "X-Correlation-ID: $CORRELATION_ID" \
  --data "$LOGIN_PAYLOAD" \
  "$CUSTOMER_BASE/api/v1/auth/login")
MANAGER_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
[[ "$MANAGER_TOKEN" == "null" || -z "$MANAGER_TOKEN" ]] && fail "Unable to retrieve manager token"

CATEGORY_NAME="Quickstart Category $(date -u '+%Y%m%d%H%M%S')"
CATEGORY_PAYLOAD=$(jq -n --arg name "$CATEGORY_NAME" --arg desc "Quickstart validation category" '{name: $name, description: $desc}')
log "Creating category: $CATEGORY_NAME"
CATEGORY_RESPONSE=$(curl --silent --show-error --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer $MANAGER_TOKEN" \
  --header "X-Correlation-ID: $CORRELATION_ID" \
  --data "$CATEGORY_PAYLOAD" \
  "$CUSTOMER_BASE/api/v1/categories")
CATEGORY_ID=$(echo "$CATEGORY_RESPONSE" | jq -r '.id')
[[ "$CATEGORY_ID" == "null" || -z "$CATEGORY_ID" ]] && fail "Category creation failed"
log "Category created: $CATEGORY_ID"

SKU="SKU$(date -u '+%H%M%S')$(printf '%02d' $((RANDOM % 100)))"
PRODUCT_NAME="Quickstart Product $(date -u '+%H%M%S')"
PRODUCT_PAYLOAD=$(jq -n \
  --arg sku "$SKU" \
  --arg name "$PRODUCT_NAME" \
  --arg desc "Automation smoke product" \
  --argjson price 19.99 \
  --argjson inventory 50 \
  --arg categoryId "$CATEGORY_ID" \
  '{sku: $sku, name: $name, description: $desc, price: $price, inventoryQuantity: $inventory, categoryId: $categoryId}')
log "Creating product: $SKU"
PRODUCT_RESPONSE=$(curl --silent --show-error --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --header "Authorization: Bearer $MANAGER_TOKEN" \
  --header "X-Correlation-ID: $CORRELATION_ID" \
  --data "$PRODUCT_PAYLOAD" \
  "$CUSTOMER_BASE/api/v1/products")
PRODUCT_ID=$(echo "$PRODUCT_RESPONSE" | jq -r '.id')
[[ "$PRODUCT_ID" == "null" || -z "$PRODUCT_ID" ]] && fail "Product creation failed"
log "Product created: $PRODUCT_ID"

log "Adding product to cart $SESSION_ID"
ADD_ITEM_PAYLOAD=$(jq -n --arg productId "$PRODUCT_ID" --argjson quantity 1 '{productId: $productId, quantity: $quantity}')
CART_RESPONSE=$(curl --silent --show-error --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --header "X-Correlation-ID: $CORRELATION_ID" \
  --data "$ADD_ITEM_PAYLOAD" \
  "$CUSTOMER_BASE/api/v1/carts/$SESSION_ID/items")
ITEM_COUNT=$(echo "$CART_RESPONSE" | jq -r '.items | length')
[[ "$ITEM_COUNT" == "0" ]] && fail "Cart update failed"
log "Cart updated; items in cart: $ITEM_COUNT"

log "Executing checkout for session $SESSION_ID"
CHECKOUT_PAYLOAD=$(jq -n \
  --arg sessionId "$SESSION_ID" \
  --arg name "Alex McKinsey" \
  --arg email "alex.mckinsey@example.com" \
  --arg phone "+14155550123" \
  --arg street "123 Market Street" \
  --arg city "San Francisco" \
  --arg state "CA" \
  --arg postal "94105" \
  --arg country "USA" \
  '{sessionId: $sessionId, customerInfo: {name: $name, email: $email, phone: $phone, shippingAddress: {street: $street, city: $city, state: $state, postalCode: $postal, country: $country}}}')
CHECKOUT_RESPONSE=$(curl --silent --show-error --fail \
  --request POST \
  --header "Content-Type: application/json" \
  --header "Idempotency-Key: $IDEMPOTENCY_KEY" \
  --header "X-Correlation-ID: $CORRELATION_ID" \
  --data "$CHECKOUT_PAYLOAD" \
  "$CUSTOMER_BASE/api/v1/checkout")
ORDER_NUMBER=$(echo "$CHECKOUT_RESPONSE" | jq -r '.orderNumber')
CHECKOUT_STATUS=$(echo "$CHECKOUT_RESPONSE" | jq -r '.status')
[[ "$ORDER_NUMBER" == "null" || -z "$ORDER_NUMBER" ]] && fail "Checkout failed"
log "Checkout accepted; order number: $ORDER_NUMBER (status: $CHECKOUT_STATUS)"

log "Polling order status"
ATTEMPT=1
ORDER_STATUS=""
while [[ $ATTEMPT -le $POLL_ATTEMPTS ]]; do
  ORDER_RESPONSE=$(curl --silent --show-error --fail \
    --request GET \
    --header "X-Correlation-ID: $CORRELATION_ID" \
    "$ORDER_BASE/api/v1/orders/$ORDER_NUMBER")
  ORDER_STATUS=$(echo "$ORDER_RESPONSE" | jq -r '.status')
  PAYMENT_STATUS=$(echo "$ORDER_RESPONSE" | jq -r '.paymentStatus')
  log "Attempt $ATTEMPT/$POLL_ATTEMPTS → status: $ORDER_STATUS, payment: $PAYMENT_STATUS"
  if [[ "$ORDER_STATUS" == "PAID" || "$ORDER_STATUS" == "FULFILLED" ]]; then
    break
  fi
  if [[ "$ORDER_STATUS" == "FAILED" || "$ORDER_STATUS" == "CANCELLED" ]]; then
    fail "Order entered terminal failure state: $ORDER_STATUS"
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep "$POLL_SLEEP_SECONDS"
done

[[ "$ORDER_STATUS" != "PAID" && "$ORDER_STATUS" != "FULFILLED" ]] && fail "Order status did not reach PAID/FULFILLED within expected window"

log "Quickstart validation succeeded for order $ORDER_NUMBER"
