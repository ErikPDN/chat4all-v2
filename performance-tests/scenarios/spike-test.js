import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

/**
 * K6 Spike Test: Sudden Traffic Surge
 * 
 * Simulates Black Friday / viral event:
 * - Baseline: 1,000 users
 * - Spike to: 10,000 users in 10 seconds
 * - Sustain: 5 minutes at peak
 * - Recover: Back to baseline
 * 
 * Success Criteria:
 * - No service crashes
 * - Error rate < 5% during spike
 * - Circuit breakers activate (check logs)
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const errorRate = new Rate('error_rate');

export const options = {
  scenarios: {
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 1000 },   // Baseline
        { duration: '10s', target: 10000 },  // SPIKE!
        { duration: '5m', target: 10000 },   // Sustain peak
        { duration: '2m', target: 1000 },    // Recover
        { duration: '30s', target: 0 },      // Ramp down
      ],
    },
  },
  
  thresholds: {
    'http_req_duration': ['p(95)<2000'],  // Allow degraded performance
    'http_req_failed': ['rate<0.05'],     // Max 5% errors
    'error_rate': ['rate<0.05'],
  },
};

export default function () {
  const payload = JSON.stringify({
    conversationId: `spike-conv-${__VU}`,
    senderId: `spike-user-${__VU}`,
    content: `Spike test message`,
    platform: 'whatsapp',
  });
  
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };
  
  const res = http.post(`${BASE_URL}/api/messages/v1/outbound`, payload, params);
  
  // Accept rate limiting and service unavailable during spike
  check(res, {
    'acceptable status': (r) => [202, 429, 503].includes(r.status),
  });
  
  if (![202, 429, 503].includes(res.status)) {
    errorRate.add(1);
  }
  
  sleep(1);
}
