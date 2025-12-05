# Chat4All Performance Tests (K6)

K6-based performance tests for the Chat4All v2 unified messaging platform.

## Why K6 over Gatling?

✅ **10x Less Memory**: ~200MB vs 2-4GB (critical for resource-constrained environments)  
✅ **Simpler Syntax**: JavaScript/ES6 (no Scala/JVM knowledge needed)  
✅ **Faster Execution**: Go-based runtime, starts in seconds  
✅ **Native Metrics**: Prometheus integration built-in  
✅ **Cloud Ready**: K6 Cloud support for distributed load testing  
✅ **Better DX**: `k6 run script.js` vs Maven compilation cycles  

## Installation

### Linux (APT)
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### macOS (Homebrew)
```bash
brew install k6
```

### Docker
```bash
docker pull grafana/k6:latest
```

### Verify Installation
```bash
k6 version
```

## Test Scenarios

### 1. Smoke Test (Quick Validation)
Fast sanity check before heavy load testing:
```bash
k6 run scenarios/smoke-test.js
```

**Purpose**: Verify all endpoints respond correctly  
**Duration**: 1 minute  
**VUs**: 10 users  
**Use When**: Before commits, CI/CD pipelines

---

### 2. Target 10,000 Requests/Minute (Throughput Test) ⭐
**NEW**: Precise RPS control to meet performance SLA:
```bash
# Quick run from repo root
./test-10k-rpm.sh

# Custom configuration
./test-10k-rpm.sh 10m 15000  # 10 minutes, 15K req/min

# Or run directly with k6
cd performance-tests
k6 run scenarios/target-10k-rpm.js
```

**Performance Targets**:
- **10,000 req/min** = 167 req/s sustained throughput
- P95 API response: < 500ms
- P99 API response: < 1000ms
- Error rate: < 1%
- Zero dropped requests

**Executor**: `constant-arrival-rate` (maintains exact RPS regardless of response time)

**Request Distribution**:
- 50% POST /api/messages (send messages)
- 30% GET /api/v1/conversations/{id}/messages (history)
- 15% GET /actuator/health (health checks)
- 5% POST /api/connectors/whatsapp/webhook (inbound)

**When to use**: 
- SLA validation (FR-012: <500ms P95)
- Capacity planning
- Production readiness testing

---

### 3. Concurrent Conversations (Load Test)
Simulates 10,000 concurrent users with realistic behavior:
```bash
# Default: 10K users, 5 minutes
k6 run scenarios/concurrent-conversations.js

# Custom configuration
k6 run scenarios/concurrent-conversations.js \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=10000 \
  -e DURATION=5m
```

**Performance Targets**:
- P95 API response: < 500ms (FR-012)
- P95 history query: < 2s (SC-009)
- Error rate: < 1%
- 10,000 concurrent conversations (SC-003)

**Load Profile**:
- Ramp to 2K users (1 min)
- Ramp to 5K users (1 min)
- Ramp to 10K users (1 min)
- Sustain 10K users (5 min)
- Ramp down (1 min)

**Behavior Mix**:
- 40% sending messages
- 30% receiving webhooks
- 20% querying history
- 10% think time

---

### 4. Spike Test (Resilience)
Tests system behavior under sudden traffic surge:
```bash
k6 run scenarios/spike-test.js
```

**Scenario**:
- Baseline: 1,000 users (30s)
- **SPIKE**: 10,000 users in 10 seconds
- Sustain: 5 minutes at peak
- Recover: 2 minutes back to baseline

**Success Criteria**:
- No crashes
- Error rate < 5%
- Circuit breakers activate
- Recovery within 2 minutes

---

## Running Tests

### Prerequisites
Ensure services are running:
```bash
docker-compose up -d
docker ps | grep chat4all  # Verify 16 containers
curl http://localhost:8080/actuator/health  # Should return UP
```

### Quick Start
```bash
cd performance-tests

# Smoke test (1 min)
k6 run scenarios/smoke-test.js

# Load test (10K users, 5 min)
k6 run scenarios/concurrent-conversations.js
```

### Custom Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | API Gateway URL |
| `VUS` | `10000` | Target virtual users |
| `DURATION` | `5m` | Test duration |

**Examples**:
```bash
# Quick 1K user test
k6 run scenarios/concurrent-conversations.js -e VUS=1000 -e DURATION=2m

# Production test
k6 run scenarios/concurrent-conversations.js \
  -e BASE_URL=https://api.chat4all.com \
  -e VUS=10000 \
  -e DURATION=10m

# Debug mode (verbose output)
k6 run scenarios/smoke-test.js --verbose
```

### Output Formats

**Terminal (default)**:
```bash
k6 run scenarios/smoke-test.js
```

**JSON (for analysis)**:
```bash
k6 run scenarios/concurrent-conversations.js --out json=results.json
```

**CSV (for Excel)**:
```bash
k6 run scenarios/concurrent-conversations.js --out csv=results.csv
```

**InfluxDB (for Grafana)**:
```bash
k6 run scenarios/concurrent-conversations.js \
  --out influxdb=http://localhost:8086/k6
```

**Prometheus RemoteWrite**:
```bash
k6 run scenarios/concurrent-conversations.js \
  -o experimental-prometheus-rw
```

---

## Analyzing Results

### Terminal Output
```
     ✓ send message: status 202
     ✓ send message: has messageId
     ✓ webhook: status 200
     ✓ history: status 200 or 404

     checks.........................: 99.50% ✓ 49750      ✗ 250
     data_received..................: 15 MB  50 kB/s
     data_sent......................: 25 MB  83 kB/s
     http_req_blocked...............: avg=1.2ms    min=0s     med=1ms    max=150ms  p(95)=3ms   
     http_req_connecting............: avg=800µs    min=0s     med=600µs  max=100ms  p(95)=2ms   
   ✓ http_req_duration..............: avg=145ms    min=12ms   med=134ms  max=498ms  p(95)=298ms ✅
     http_req_failed................: 1.00%  ✓ 500        ✗ 49500
     http_req_receiving.............: avg=120µs    min=0s     med=100µs  max=10ms   p(95)=300µs 
     http_req_sending...............: avg=80µs     min=0s     med=60µs   max=5ms    p(95)=200µs 
     http_req_tls_handshaking.......: avg=0s       min=0s     med=0s     max=0s     p(95)=0s    
     http_req_waiting...............: avg=144ms    min=12ms   med=133ms  max=497ms  p(95)=297ms 
     http_reqs......................: 50000  166.67/s
     iteration_duration.............: avg=2.5s     min=1s     med=2.3s   max=8s     p(95)=4.2s  
     iterations.....................: 50000  166.67/s
     vus............................: 10000  min=0        max=10000
     vus_max........................: 10000  min=10000    max=10000
```

**Key Metrics**:
- ✅ `http_req_duration p(95)`: Must be < 500ms
- ✅ `http_req_failed`: Must be < 1%
- ✅ `checks`: Should be > 95%

### Grafana Dashboards

If using Prometheus/InfluxDB output, visualize in Grafana:

**Import Dashboard**: K6 Load Testing Results (ID: 2587)

**Panels to Monitor**:
1. **Request Rate**: Requests/second over time
2. **Response Time Percentiles**: P50, P95, P99
3. **Error Rate**: Failed requests over time
4. **Virtual Users**: Active VUs over time
5. **Custom Metrics**: `messages_sent`, `messages_received`, `api_response_time`

### Troubleshooting

**Error: `Connection refused`**
```bash
# Verify API Gateway
docker logs chat4all-api-gateway --tail 50
curl http://localhost:8080/actuator/health
```

**High Error Rate (> 5%)**
```bash
# Check service logs
docker logs chat4all-message-service --tail 100 | grep ERROR
docker logs chat4all-router-service --tail 100 | grep ERROR

# Check Kafka consumer lag
docker exec chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group router-service-group
```

**Slow Response Times**
```bash
# Monitor services in real-time
docker stats

# Check MongoDB performance
docker exec chat4all-mongodb mongosh --eval \
  "db.getSiblingDB('chat4all').currentOp({secs_running: {$gte: 1}})"

# Check PostgreSQL connections
docker exec chat4all-postgres psql -U chat4all -c \
  "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"
```

---

## CI/CD Integration

### GitHub Actions
```yaml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly on Sunday 2 AM
  workflow_dispatch:

jobs:
  k6-load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Start services
        run: docker-compose up -d
      
      - name: Wait for services
        run: sleep 60
      
      - name: Install K6
        run: |
          sudo gpg -k
          sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
          echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
          sudo apt-get update
          sudo apt-get install k6
      
      - name: Run smoke test
        run: k6 run performance-tests/scenarios/smoke-test.js
      
      - name: Run load test
        run: |
          k6 run performance-tests/scenarios/concurrent-conversations.js \
            -e VUS=1000 \
            -e DURATION=2m \
            --out json=results.json
      
      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: k6-results
          path: results.json
```

### Docker Compose (Isolated)
```bash
# Run K6 in container
docker run --rm \
  --network chat4all-v2_default \
  -v $(pwd)/performance-tests:/scripts \
  grafana/k6:latest run /scripts/scenarios/smoke-test.js \
  -e BASE_URL=http://chat4all-api-gateway:8080
```

---

## Performance Tuning

### Before Running Tests

1. **Disable debug logging**: Set all services to INFO level
2. **Warm up services**: Run smoke test first
3. **Clear old data**: Truncate test conversations if reusing environment
4. **Monitor resources**: Have `htop` running on host

### System Requirements

| Test Type | VUs | RAM Needed | CPU Cores | Network |
|-----------|-----|------------|-----------|---------|
| Smoke | 10 | ~50MB | 1 | Any |
| Load (1K) | 1,000 | ~100MB | 2 | Good |
| Load (10K) | 10,000 | ~200MB | 4+ | Excellent |
| Spike | 10,000 | ~250MB | 4+ | Excellent |

**K6 vs Gatling Memory**:
- K6 (10K VUs): ~200MB
- Gatling (10K VUs): ~4GB ⚠️

### Scaling Services

If tests fail targets, scale horizontally:
```bash
# Scale message-service
docker-compose up -d --scale message-service=3

# Scale router-service
docker-compose up -d --scale router-service=3

# Re-run test
k6 run scenarios/concurrent-conversations.js
```

---

## Success Criteria Checklist

- [ ] **SC-003**: System handles 10,000 concurrent conversations
- [ ] **P95 API Response**: < 500ms across all endpoints
- [ ] **P95 History Retrieval**: < 2s for 100 messages
- [ ] **Success Rate**: > 99% (< 1% errors)
- [ ] **No Crashes**: All containers remain healthy
- [ ] **Kafka Lag**: < 10,000 messages throughout test
- [ ] **Memory Stable**: No heap growth over 1-hour test

---

## Related Documentation

- [Architecture Plan](../specs/001-unified-messaging-platform/plan.md) - Performance targets
- [Scaling Runbook](../docs/runbooks/scaling.md) - How to scale under load
- [Message Delivery Failure Runbook](../docs/runbooks/message-delivery-failure.md)
- [ADR-001: Dual-Database Architecture](../docs/adr/001-dual-database-architecture.md)

---

## K6 Resources

- [K6 Documentation](https://k6.io/docs/)
- [K6 Examples](https://github.com/grafana/k6-learn)
- [K6 Cloud](https://k6.io/cloud/) - Distributed load testing
- [K6 Extensions](https://k6.io/docs/extensions/) - Kafka, WebSockets, etc.
