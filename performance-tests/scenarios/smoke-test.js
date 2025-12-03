import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * K6 Smoke Test: Basic Functionality Validation
 * 
 * Quick test to verify system is working before heavy load:
 * - 10 users for 1 minute
 * - Tests all critical endpoints
 * - Fast feedback for CI/CD
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 10,
  duration: '1m',
  
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
    'checks': ['rate>0.95'],
  },
};

export function setup() {
  // Verify API Gateway is up
  const res = http.get(`${BASE_URL}/actuator/health`);
  check(res, {
    'API Gateway is UP': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.status === 'UP';
      } catch {
        return false;
      }
    },
  });
  
  return { healthy: res.status === 200 };
}

export default function (data) {
  if (!data.healthy) {
    throw new Error('API Gateway not healthy, aborting test');
  }
  
  // Test 1: Health endpoint (public)
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health check OK': (r) => r.status === 200,
    'health response valid JSON': (r) => {
      try {
        JSON.parse(r.body);
        return true;
      } catch {
        return false;
      }
    },
  });
  
  sleep(0.5);
  
  // Test 2: Prometheus metrics endpoint (public)
  const metricsRes = http.get(`${BASE_URL}/actuator/prometheus`);
  check(metricsRes, {
    'metrics endpoint OK': (r) => r.status === 200,
    'metrics response time OK': (r) => r.timings.duration < 500,
  });
  
  sleep(0.5);
  
  // Test 3: OpenAPI docs endpoint (public)
  const docsRes = http.get(`${BASE_URL}/v3/api-docs`);
  check(docsRes, {
    'OpenAPI docs OK': (r) => r.status === 200,
  });
  
  sleep(1);
}
