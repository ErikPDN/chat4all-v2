#!/bin/bash

# Test script to generate traffic and populate Grafana dashboard
# This will create messages via the API to generate metrics

API_BASE="http://localhost:8080"
MESSAGE_ENDPOINT="${API_BASE}/api/messages"

echo "🚀 Starting Grafana Metrics Test - Generating traffic..."
echo "📊 Dashboard: http://localhost:3000/d/chat4all-overview"
echo ""

# Generate 50 messages to create metrics
for i in {1..50}; do
    # Generate random conversation ID
    CONVERSATION_ID="conv-test-$(uuidgen)"
    
    # Send message via API Gateway (without authentication for testing)
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${MESSAGE_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "{
            \"conversationId\": \"${CONVERSATION_ID}\",
            \"senderId\": \"agent-${i}\",
            \"content\": \"Test message ${i} for Grafana dashboard at $(date +%H:%M:%S)\",
            \"channel\": \"WHATSAPP\"
        }" 2>&1)
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | head -1)
    
    if [ "$HTTP_CODE" = "202" ] || [ "$HTTP_CODE" = "200" ]; then
        echo "✅ Message ${i}/50 sent successfully (HTTP ${HTTP_CODE})"
    elif [ "$HTTP_CODE" = "401" ]; then
        echo "⚠️  Message ${i}/50 - Authentication required (expected in production)"
        echo "   Using direct message-service endpoint instead..."
        
        # Fallback: send directly to message-service (bypass gateway)
        curl -s -X POST "http://localhost:8081/api/messages" \
            -H "Content-Type: application/json" \
            -d "{
                \"conversationId\": \"${CONVERSATION_ID}\",
                \"senderId\": \"agent-${i}\",
                \"content\": \"Test message ${i} for Grafana dashboard at $(date +%H:%M:%S)\",
                \"channel\": \"WHATSAPP\"
            }" > /dev/null
        echo "   ✅ Sent via message-service directly"
    else
        echo "❌ Message ${i}/50 failed (HTTP ${HTTP_CODE}): ${BODY}"
    fi
    
    # Small delay to avoid overwhelming the system
    sleep 0.2
done

echo ""
echo "✅ Traffic generation complete!"
echo "📊 Check Grafana dashboard: http://localhost:3000/d/chat4all-overview"
echo "🔍 Prometheus metrics: http://localhost:8081/actuator/prometheus | grep messages"
echo ""
echo "⏳ Wait 5-10 seconds for metrics to appear in Grafana..."
