import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * K6 Performance Test: 10,000 Concurrent Conversations
 * 
 * Performance Targets (from plan.md):
 * - <500ms API response time (P95)
 * - <5s message delivery to external platforms (P95)
 * - 10,000 concurrent conversations (SC-003)
 * - <2s conversation history retrieval (SC-009)
 * 
 * K6 Advantages over Gatling:
 * - 10x less memory consumption (~200MB vs 2GB+)
 * - JavaScript/ES6 syntax (easier to maintain)
 * - Native Prometheus integration
 * - Cloud execution support (k6 Cloud)
 */

// Custom metrics
const messagesSent = new Counter('messages_sent');
const messagesReceived = new Counter('messages_received');
const apiResponseTime = new Trend('api_response_time');
const errorRate = new Rate('error_rate');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_DURATION = __ENV.DURATION || '5m';
const TARGET_VUS = parseInt(__ENV.VUS || '10000');

// Load profile options
export const options = {
  scenarios: {
    // Scenario 1: Ramped load test (default)
    ramped_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: TARGET_VUS * 0.2 },  // Ramp to 20%
        { duration: '1m', target: TARGET_VUS * 0.5 },  // Ramp to 50%
        { duration: '1m', target: TARGET_VUS },        // Ramp to 100%
        { duration: TEST_DURATION, target: TARGET_VUS }, // Sustain
        { duration: '1m', target: 0 },                 // Ramp down
      ],
      gracefulRampDown: '30s',
    },
  },
  
  // Performance thresholds (test fails if not met)
  thresholds: {
    'http_req_duration': ['p(95)<500'],           // P95 < 500ms (FR-012)
    'http_req_duration{endpoint:history}': ['p(95)<2000'], // History < 2s (SC-009)
    'http_req_failed': ['rate<0.01'],             // Error rate < 1%
    'error_rate': ['rate<0.01'],
  },
  
  // HTTP settings
  insecureSkipTLSVerify: true,
  noConnectionReuse: false,
};

// Test setup (runs once)
export function setup() {
  console.log(`Starting performance test against ${BASE_URL}`);
  console.log(`Target VUs: ${TARGET_VUS}, Duration: ${TEST_DURATION}`);
  
  // Health check
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  if (healthRes.status !== 200) {
    throw new Error(`API Gateway not healthy: ${healthRes.status}`);
  }
  
  return { startTime: Date.now() };
}

// Main test iteration (runs for each VU)
export default function (data) {
  const conversationId = `conv-${__VU}-${__ITER}`;
  
  // 40% chance: Send outbound message
  if (Math.random() < 0.4) {
    sendOutboundMessage(conversationId);
  }
  
  // 30% chance: Simulate inbound webhook
  else if (Math.random() < 0.7) {
    receiveInboundMessage(conversationId);
  }
  
  // 20% chance: Get conversation history
  else if (Math.random() < 0.9) {
    getConversationHistory(conversationId);
  }
  
  // 10% chance: Just think time
  else {
    sleep(Math.random() * 3 + 1); // 1-4 seconds
  }
}

// Scenario functions
function sendOutboundMessage(conversationId) {
  const payload = JSON.stringify({
    conversationId: conversationId,
    senderId: `user-${__VU}`,
    content: `Performance test message from VU ${__VU} iteration ${__ITER}`,
    platform: 'whatsapp',
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'send_message' },
  };
  
  const res = http.post(`${BASE_URL}/api/messages/v1/outbound`, payload, params);
  
  const success = check(res, {
    'send message: status 202': (r) => r.status === 202,
    'send message: has messageId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.messageId !== undefined;
      } catch {
        return false;
      }
    },
  });
  
  if (success) {
    messagesSent.add(1);
  } else {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
  sleep(Math.random() * 2 + 1); // 1-3 seconds think time
}

function receiveInboundMessage(conversationId) {
  const payload = JSON.stringify({
    object: 'whatsapp_business_account',
    entry: [{
      id: '123456789',
      changes: [{
        value: {
          messaging_product: 'whatsapp',
          metadata: {
            display_phone_number: '15551234567',
            phone_number_id: '987654321',
          },
          messages: [{
            from: '5511999887766',
            id: `wamid-${__VU}-${__ITER}`,
            timestamp: Math.floor(Date.now() / 1000).toString(),
            type: 'text',
            text: {
              body: `Inbound test from VU ${__VU}`,
            },
          }],
        },
        field: 'messages',
      }],
    }],
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'webhook' },
  };
  
  const res = http.post(`${BASE_URL}/api/connectors/whatsapp/webhook`, payload, params);
  
  const success = check(res, {
    'webhook: status 200': (r) => r.status === 200,
  });
  
  if (success) {
    messagesReceived.add(1);
  } else {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
  sleep(Math.random() * 3 + 2); // 2-5 seconds think time
}

function getConversationHistory(conversationId) {
  const params = {
    tags: { endpoint: 'history' },
  };
  
  const res = http.get(
    `${BASE_URL}/api/messages/v1/conversations/${conversationId}/messages?limit=100`,
    params
  );
  
  check(res, {
    'history: status 200 or 404': (r) => r.status === 200 || r.status === 404,
    'history: response time < 2s': (r) => r.timings.duration < 2000,
  });
  
  if (res.status !== 200 && res.status !== 404) {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
  sleep(Math.random() * 5 + 5); // 5-10 seconds think time
}

// Test teardown (runs once at end)
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Test completed in ${duration.toFixed(2)} seconds`);
}
