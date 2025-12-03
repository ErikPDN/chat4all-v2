#!/bin/bash

# Complete Grafana Dashboard Test
# Generates traffic to populate all metrics in the dashboard

set -e

API_BASE="http://localhost:8080"
MESSAGE_SERVICE="http://localhost:8081"

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  Complete Grafana Dashboard Test - Chat4All v2"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Step 1: Check if mock connectors are running
echo "🔍 Step 1: Checking mock connectors..."
MOCK_RUNNING=false

for PORT in 8085 8086 8087; do
    if curl -s "http://localhost:${PORT}/health" > /dev/null 2>&1; then
        echo "   ✅ Mock connector on port ${PORT} is running"
        MOCK_RUNNING=true
    else
        echo "   ⚠️  Mock connector on port ${PORT} is not running"
    fi
done

if [ "$MOCK_RUNNING" = false ]; then
    echo ""
    echo "❌ No mock connectors detected!"
    echo ""
    echo "Please start the mock connectors in another terminal:"
    echo "   node mock-connectors-server.js"
    echo ""
    echo "Or continue without mocks (messages will fail to route):"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "📨 Step 2: Generating message traffic..."
echo "   Target: 100 messages across 3 channels"
echo ""

SUCCESS_COUNT=0
FAIL_COUNT=0

# Generate messages for different channels
CHANNELS=("WHATSAPP" "TELEGRAM" "INSTAGRAM")

for i in {1..100}; do
    # Round-robin through channels
    CHANNEL_INDEX=$((($i - 1) % 3))
    CHANNEL="${CHANNELS[$CHANNEL_INDEX]}"
    
    CONVERSATION_ID="conv-grafana-test-$(uuidgen)"
    
    # Send directly to message-service (bypass auth)
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${MESSAGE_SERVICE}/api/messages" \
        -H "Content-Type: application/json" \
        -d "{
            \"conversationId\": \"${CONVERSATION_ID}\",
            \"senderId\": \"agent-${i}\",
            \"content\": \"Grafana test message ${i} via ${CHANNEL} at $(date +%H:%M:%S)\",
            \"channel\": \"${CHANNEL}\"
        }" 2>&1)
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    
    if [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        printf "\r   Progress: ${i}/100 messages (✅ ${SUCCESS_COUNT} success, ❌ ${FAIL_COUNT} failed)"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        printf "\r   Progress: ${i}/100 messages (✅ ${SUCCESS_COUNT} success, ❌ ${FAIL_COUNT} failed)"
    fi
    
    # Small delay to simulate realistic traffic
    sleep 0.1
done

echo ""
echo ""
echo "✅ Traffic generation complete!"
echo "   Total:   100 messages"
echo "   Success: ${SUCCESS_COUNT}"
echo "   Failed:  ${FAIL_COUNT}"
echo ""

# Step 3: Wait for metrics to be scraped
echo "⏳ Step 3: Waiting for metrics to propagate..."
echo "   Prometheus scrapes every 5 seconds"

for i in {5..1}; do
    printf "\r   Waiting ${i} seconds...  "
    sleep 1
done
printf "\r   ✅ Ready!                  \n"
echo ""

# Step 4: Verify metrics are available
echo "🔍 Step 4: Verifying metrics in Prometheus..."

METRICS_FOUND=0

# Check messages_inbound_total
if curl -s "http://localhost:9090/api/v1/query?query=messages_inbound_total" | jq -e '.data.result | length > 0' > /dev/null 2>&1; then
    INBOUND=$(curl -s "http://localhost:9090/api/v1/query?query=messages_inbound_total" | jq -r '.data.result[0].value[1]')
    echo "   ✅ messages_inbound_total: ${INBOUND}"
    METRICS_FOUND=$((METRICS_FOUND + 1))
else
    echo "   ❌ messages_inbound_total: Not found"
fi

# Check messages_processed_success_total
if curl -s "http://localhost:9090/api/v1/query?query=messages_processed_success_total" | jq -e '.data.result | length > 0' > /dev/null 2>&1; then
    PROCESSED=$(curl -s "http://localhost:9090/api/v1/query?query=messages_processed_success_total" | jq -r '.data.result[0].value[1]')
    echo "   ✅ messages_processed_success_total: ${PROCESSED}"
    METRICS_FOUND=$((METRICS_FOUND + 1))
else
    echo "   ❌ messages_processed_success_total: Not found"
fi

# Check messages_routed_total
if curl -s "http://localhost:9090/api/v1/query?query=messages_routed_total" | jq -e '.data.result | length > 0' > /dev/null 2>&1; then
    ROUTED=$(curl -s "http://localhost:9090/api/v1/query?query=sum(messages_routed_total)" | jq -r '.data.result[0].value[1]')
    echo "   ✅ messages_routed_total: ${ROUTED}"
    METRICS_FOUND=$((METRICS_FOUND + 1))
else
    echo "   ⚠️  messages_routed_total: Not found (router may not be registering metrics)"
fi

echo ""

if [ $METRICS_FOUND -ge 2 ]; then
    echo "✅ Metrics are available! Dashboard should show data."
else
    echo "⚠️  Some metrics are missing. Dashboard may show partial data."
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  📊 View Results"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  Grafana Dashboard:"
echo "    http://localhost:3000/d/chat4all-overview"
echo ""
echo "  Prometheus Metrics:"
echo "    http://localhost:9090/graph?g0.expr=messages_inbound_total"
echo "    http://localhost:9090/graph?g0.expr=messages_processed_success_total"
echo ""
echo "  Mock Connector Stats:"
echo "    http://localhost:8085/stats (WhatsApp)"
echo "    http://localhost:8086/stats (Telegram)"
echo "    http://localhost:8087/stats (Instagram)"
echo ""
echo "═══════════════════════════════════════════════════════════"
echo ""
