import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * K6 Performance Test: 10,000 Requests Per Minute
 * 
 * Target: 10,000 req/min = ~167 req/s
 * Strategy: Use constant-arrival-rate executor for precise RPS control
 * 
 * Performance Targets:
 * - 10,000 requests/minute sustained throughput
 * - <500ms API response time (P95)
 * - <1% error rate
 * - Zero dropped requests
 */

// Custom metrics
const messagesSent = new Counter('messages_sent');
const messagesDelivered = new Counter('messages_delivered');
const apiResponseTime = new Trend('api_response_time');
const errorRate = new Rate('error_rate');
const requestsPerSecond = new Rate('requests_per_second');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_RPM = parseInt(__ENV.TARGET_RPM || '10000');
const TARGET_RPS = Math.ceil(TARGET_RPM / 60); // 167 req/s
const TEST_DURATION = __ENV.DURATION || '5m';
const RAMP_DURATION = __ENV.RAMP_DURATION || '2m';

console.log(`Target: ${TARGET_RPM} req/min = ${TARGET_RPS} req/s`);

export const options = {
  scenarios: {
    // Constant arrival rate: maintains exact RPS regardless of response time
    constant_throughput: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,              // 167 requests per second
      timeUnit: '1s',                // per second
      duration: TEST_DURATION,       // sustain for 5 minutes
      preAllocatedVUs: 200,          // pre-allocate VUs (adjust based on response time)
      maxVUs: 500,                   // max VUs if needed (safety limit)
    },
  },
  
  // Performance thresholds
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],  // P95 < 500ms, P99 < 1s
    'http_req_failed': ['rate<0.01'],                   // < 1% errors
    'error_rate': ['rate<0.01'],                        // < 1% custom errors
    'http_reqs': [`rate>=${TARGET_RPS * 0.95}`],       // >= 95% of target RPS
  },
  
  // HTTP settings for high throughput
  insecureSkipTLSVerify: true,
  noConnectionReuse: false,           // Reuse connections (HTTP keep-alive)
  userAgent: 'k6-performance-test/1.0',
  batch: 10,                          // Batch requests for efficiency
  batchPerHost: 10,
};

// Test setup
export function setup() {
  console.log(`=== K6 Performance Test Configuration ===`);
  console.log(`Target: ${TARGET_RPM} req/min (${TARGET_RPS} req/s)`);
  console.log(`Duration: ${TEST_DURATION}`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`==========================================`);
  
  // Verify API Gateway health
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  if (healthRes.status !== 200) {
    throw new Error(`API Gateway not healthy: ${healthRes.status}`);
  }
  
  console.log('✓ API Gateway is healthy');
  return { startTime: Date.now() };
}

// Main test iteration
export default function (data) {
  const conversationId = `conv-load-${(__VU % 1000)}-${Math.floor(__ITER / 100)}`;
  const userId = `user-${__VU % 500}`; // Distribute across 500 users
  
  // Request distribution (optimized for realistic load):
  // 50% - Send messages (POST /api/messages)
  // 30% - Get conversation history (GET /api/conversations/{id}/messages)
  // 15% - Health checks (GET /actuator/health)
  // 5%  - Webhook simulation (POST /api/connectors/whatsapp/webhook)
  
  const rand = Math.random();
  
  if (rand < 0.50) {
    // Send message (most common operation)
    sendMessage(conversationId, userId);
  } else if (rand < 0.80) {
    // Get conversation history
    getHistory(conversationId);
  } else if (rand < 0.95) {
    // Health check
    healthCheck();
  } else {
    // Webhook (inbound message)
    receiveWebhook(conversationId, userId);
  }
  
  requestsPerSecond.add(1);
  
  // No sleep - constant-arrival-rate handles timing
}

function sendMessage(conversationId, userId) {
  const payload = JSON.stringify({
    conversationId: conversationId,
    senderId: userId,
    content: `Load test message ${__ITER}`,
    channel: 'WHATSAPP',
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'send_message', operation: 'POST' },
  };
  
  const res = http.post(`${BASE_URL}/api/messages`, payload, params);
  
  const success = check(res, {
    'send: status 202': (r) => r.status === 202,
    'send: has messageId': (r) => {
      try {
        return JSON.parse(r.body).messageId !== undefined;
      } catch {
        return false;
      }
    },
    'send: response < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (success) {
    messagesSent.add(1);
  } else {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
}

function getHistory(conversationId) {
  const params = {
    tags: { name: 'get_history', operation: 'GET' },
  };
  
  const res = http.get(
    `${BASE_URL}/api/v1/conversations/${conversationId}/messages?limit=20`,
    params
  );
  
  const success = check(res, {
    'history: status ok': (r) => r.status === 200 || r.status === 404,
    'history: response < 500ms': (r) => r.timings.duration < 500,
  });
  
  if (!success) {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
}

function healthCheck() {
  const params = {
    tags: { name: 'health_check', operation: 'GET' },
  };
  
  const res = http.get(`${BASE_URL}/actuator/health`, params);
  
  check(res, {
    'health: status 200': (r) => r.status === 200,
    'health: response < 100ms': (r) => r.timings.duration < 100,
  });
  
  apiResponseTime.add(res.timings.duration);
}

function receiveWebhook(conversationId, userId) {
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
              body: `Webhook test ${__ITER}`,
            },
          }],
        },
        field: 'messages',
      }],
    }],
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'webhook', operation: 'POST' },
  };
  
  const res = http.post(`${BASE_URL}/api/connectors/whatsapp/webhook`, payload, params);
  
  const success = check(res, {
    'webhook: status 200': (r) => r.status === 200,
  });
  
  if (success) {
    messagesDelivered.add(1);
  } else {
    errorRate.add(1);
  }
  
  apiResponseTime.add(res.timings.duration);
}

// Test teardown
export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n=== Test Summary ===`);
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log(`Target: ${TARGET_RPM} req/min (${TARGET_RPS} req/s)`);
  console.log(`====================`);
}
