# Runbook: Scaling Services During Traffic Spikes

**Service**: Chat4All v2 - Unified Messaging Platform  
**Last Updated**: 2025-12-03  
**Severity**: P2 (High - impacts performance and user experience)

## Overview

This runbook provides procedures for scaling Chat4All services during traffic spikes, planned events, or gradual growth. The platform uses Docker Compose for local/staging environments and can be adapted for production orchestration platforms.

---

## Prerequisites

- Docker and Docker Compose installed
- Access to server/VM with sufficient resources
- Access to monitoring dashboards (Grafana: http://localhost:3000)
- Understanding of service dependencies and resource requirements
- Backup of current docker-compose.yml configuration

---

## Quick Reference: Service Scaling Priority

**High Priority** (scale first during traffic spikes):
1. **API Gateway** - All traffic flows through here
2. **Message Service** - Core messaging functionality
3. **Connectors** (WhatsApp/Telegram/Instagram) - External platform integration
4. **Router Service** - Message routing and event processing

**Medium Priority** (scale if specific features are heavily used):
5. **File Service** - If file uploads/downloads are high
6. **User Service** - If user authentication/profile requests are high

**Infrastructure** (scale based on resource metrics):
7. **Kafka** - If broker CPU/disk is saturated
8. **MongoDB** - If database connections are exhausted
9. **PostgreSQL** - If relational queries are slow
10. **Redis** - If cache hit rate is low or memory is full

---

## Monitoring Indicators for Scaling

### Traffic Metrics

```bash
# Check current request rate (requests per minute)
curl -s http://localhost:8080/actuator/metrics/http.server.requests | \
  jq '.measurements[] | select(.statistic == "COUNT") | .value'

# Check active connections
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"

# Check Grafana dashboard for detailed metrics
# Navigate to: http://localhost:3000/d/chat4all-overview
```

### Resource Utilization Thresholds

**Scale UP when:**
- CPU usage >70% sustained for 5+ minutes
- Memory usage >80% sustained for 5+ minutes
- Response time P95 >2 seconds
- Error rate >1% of requests
- Kafka consumer lag >1000 messages
- Database connection pool >80% utilized

**Scale DOWN when:**
- CPU usage <30% sustained for 15+ minutes
- Memory usage <50% sustained for 15+ minutes
- All metrics healthy with headroom

---

## Scenario 1: Emergency Scaling (Traffic Spike in Progress)

### Indicators
- Sudden increase in request rate (>2x normal)
- Response times degrading (P95 >5 seconds)
- Error rate spiking (>5%)
- User complaints about slowness

### Quick Actions

#### 1.1 Immediate Triage

```bash
# Check overall system health
docker-compose ps

# Check resource usage of all services
docker stats --no-stream

# Identify bottleneck service (highest CPU/memory)
docker stats --no-stream --format "{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | sort -k2 -rn | head -5
```

#### 1.2 Scale API Gateway (handles all incoming traffic)

```bash
# Increase API Gateway instances to 3
docker-compose up -d --scale api-gateway=3

# Verify all instances are running
docker ps --filter name=api-gateway --format "{{.Names}}\t{{.Status}}"

# Check logs for startup success
docker-compose logs api-gateway --tail=50 | grep "Started"

# Wait 30 seconds for health checks
sleep 30

# Verify load balancing (if using nginx/traefik)
# Check that requests are distributed across instances
```

**Expected Result**: Request load distributed across 3 instances, CPU per instance drops to ~33% of original.

#### 1.3 Scale Message Service (core messaging logic)

```bash
# Increase Message Service instances to 3
docker-compose up -d --scale message-service=3

# Verify instances
docker ps --filter name=message-service

# Check Kafka consumer group rebalancing
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group message-service-group \
  --describe

# Monitor consumer lag - should decrease as partitions are redistributed
watch -n 5 'docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group message-service-group \
  --describe | grep -E "TOPIC|LAG"'
```

**Expected Result**: Consumer lag decreases, message processing throughput increases.

#### 1.4 Scale Active Connectors

```bash
# Identify which connectors are active (check logs for traffic)
docker-compose logs --tail=100 whatsapp-connector telegram-connector instagram-connector | \
  grep "Processing message" | awk '{print $1}' | sort | uniq -c

# Scale the most active connector (example: WhatsApp)
docker-compose up -d --scale whatsapp-connector=2

# Verify connector instances
docker ps --filter name=whatsapp-connector

# Check Kafka consumer group for connector
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group whatsapp-connector-group \
  --describe
```

**Expected Result**: Connector processing rate doubles, outbound message latency decreases.

#### 1.5 Verify Scaling Effectiveness

```bash
# Check request latency (should be decreasing)
curl -s http://localhost:8080/actuator/metrics/http.server.requests | \
  jq '.measurements[] | select(.statistic == "MAX") | .value'

# Check error rate (should be decreasing)
docker-compose logs --since 5m api-gateway message-service | \
  grep -c "ERROR"

# Check Grafana dashboard
# Navigate to: http://localhost:3000/d/chat4all-performance
# Verify P95 latency trending downward
```

---

## Scenario 2: Planned Scaling (Before Expected Traffic)

### Use Cases
- Marketing campaign launch
- Product announcement
- Scheduled maintenance window
- Load testing preparation

### Pre-Event Checklist

```bash
# 1. Verify current resource availability
free -h                    # Check available memory
df -h                      # Check disk space
docker system df           # Check Docker disk usage

# 2. Clean up unused Docker resources
docker system prune -a --volumes --force

# 3. Backup current configuration
cp docker-compose.yml docker-compose.yml.backup.$(date +%Y%m%d_%H%M%S)

# 4. Verify all services are healthy
docker-compose ps
docker-compose logs --tail=100 | grep -i "error\|exception\|fatal"
```

### Scaling Plan

#### 2.1 Calculate Target Capacity

**Example Calculation**:
- Current capacity: 1000 messages/minute
- Expected peak: 5000 messages/minute
- Required scale: 5x

**Service Instances** (rule of thumb):
- API Gateway: `ceil(peak_rps / 500)` = 10 RPS → 1 instance, 100 RPS → 2 instances
- Message Service: `ceil(peak_msg_rate / 1000)` = 5000 msgs → 5 instances
- Connectors: `ceil(peak_outbound_rate / 500)` per connector
- Router Service: Usually 1-2 instances (Kafka handles parallelism via partitions)

#### 2.2 Update docker-compose.yml for Scaling

```yaml
# Add deploy configuration to docker-compose.yml
services:
  api-gateway:
    # ... existing config ...
    deploy:
      replicas: 3  # Scale to 3 instances
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M

  message-service:
    # ... existing config ...
    deploy:
      replicas: 5  # Scale to 5 instances
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G

  whatsapp-connector:
    # ... existing config ...
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M

  router-service:
    # ... existing config ...
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1.5'
          memory: 1.5G
        reservations:
          cpus: '0.75'
          memory: 768M
```

**Note**: Docker Compose deploy.replicas requires Docker Swarm mode. For non-swarm, use `--scale` flag:

```bash
docker-compose up -d \
  --scale api-gateway=3 \
  --scale message-service=5 \
  --scale whatsapp-connector=3 \
  --scale telegram-connector=2 \
  --scale router-service=2
```

#### 2.3 Gradual Scale-Up (Recommended)

```bash
# Step 1: Scale to 50% of target (2 hours before event)
docker-compose up -d \
  --scale api-gateway=2 \
  --scale message-service=3 \
  --scale whatsapp-connector=2

# Monitor for 15 minutes
docker stats --no-stream

# Step 2: Scale to 75% of target (1 hour before event)
docker-compose up -d \
  --scale api-gateway=2 \
  --scale message-service=4 \
  --scale whatsapp-connector=2

# Monitor for 15 minutes

# Step 3: Scale to 100% of target (30 minutes before event)
docker-compose up -d \
  --scale api-gateway=3 \
  --scale message-service=5 \
  --scale whatsapp-connector=3

# Validate all instances healthy
docker-compose ps
docker-compose logs --tail=50 | grep "Started"
```

#### 2.4 Load Balancer Configuration (if using nginx/traefik)

**Nginx upstream configuration** (`/etc/nginx/conf.d/chat4all.conf`):

```nginx
upstream api_gateway {
    least_conn;  # Use least connections algorithm
    server chat4all-api-gateway-1:8080 max_fails=3 fail_timeout=30s;
    server chat4all-api-gateway-2:8080 max_fails=3 fail_timeout=30s;
    server chat4all-api-gateway-3:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    server_name api.chat4all.com;

    location / {
        proxy_pass http://api_gateway;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Health check
        proxy_next_upstream error timeout http_500 http_502 http_503;
        proxy_connect_timeout 5s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

Reload nginx:
```bash
nginx -t && nginx -s reload
```

---

## Scenario 3: Infrastructure Scaling (Database, Kafka, Redis)

### 3.1 MongoDB Scaling

**Symptoms**:
- Slow query response times (>100ms)
- Connection pool exhaustion
- CPU >80% on MongoDB container

**Vertical Scaling** (increase resources):

```bash
# Stop MongoDB (CAUTION: This causes downtime)
docker-compose stop mongodb

# Update docker-compose.yml
services:
  mongodb:
    # ... existing config ...
    deploy:
      resources:
        limits:
          cpus: '4.0'      # Increased from 2.0
          memory: 8G       # Increased from 4G
        reservations:
          cpus: '2.0'
          memory: 4G

# Restart MongoDB
docker-compose up -d mongodb

# Verify startup
docker logs chat4all-mongodb --tail=100 | grep "waiting for connections"

# Check performance
docker exec -it chat4all-mongodb mongosh chat4all --eval "
  db.serverStatus().connections
"
```

**Horizontal Scaling** (replica set - production only):

```yaml
# docker-compose.yml - MongoDB Replica Set
services:
  mongodb-primary:
    image: mongo:7.0
    command: mongod --replSet rs0 --bind_ip_all
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD}
    volumes:
      - mongodb-primary-data:/data/db
    networks:
      - chat4all-network

  mongodb-secondary-1:
    image: mongo:7.0
    command: mongod --replSet rs0 --bind_ip_all
    volumes:
      - mongodb-secondary-1-data:/data/db
    networks:
      - chat4all-network

  mongodb-secondary-2:
    image: mongo:7.0
    command: mongod --replSet rs0 --bind_ip_all
    volumes:
      - mongodb-secondary-2-data:/data/db
    networks:
      - chat4all-network
```

Initialize replica set:
```bash
docker exec -it chat4all-mongodb-primary mongosh --eval "
rs.initiate({
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongodb-primary:27017' },
    { _id: 1, host: 'mongodb-secondary-1:27017' },
    { _id: 2, host: 'mongodb-secondary-2:27017' }
  ]
})
"
```

### 3.2 Kafka Scaling

**Symptoms**:
- High broker CPU (>80%)
- Disk I/O saturation
- Consumer lag not decreasing despite scaling consumers

**Add Kafka Brokers** (for multi-broker setup):

```yaml
# docker-compose.yml
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      # ... other configs ...
    volumes:
      - kafka-1-data:/var/lib/kafka/data

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9093
      # ... other configs ...
    volumes:
      - kafka-2-data:/var/lib/kafka/data

  kafka-3:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:9094
      # ... other configs ...
    volumes:
      - kafka-3-data:/var/lib/kafka/data
```

**Increase Topic Partitions** (no downtime):

```bash
# Check current partition count
docker exec -it chat4all-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic message-events

# Increase partitions (example: 10 → 20)
docker exec -it chat4all-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --alter --topic message-events \
  --partitions 20

# Verify change
docker exec -it chat4all-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic message-events

# Restart consumers to rebalance
docker-compose restart message-service router-service
```

**IMPORTANT**: More partitions = more parallelism, but also more overhead. Balance based on consumer count.

### 3.3 PostgreSQL Scaling

**Symptoms**:
- Slow authentication queries
- Connection pool exhaustion
- High transaction lock wait times

**Vertical Scaling**:

```bash
# Stop PostgreSQL (CAUTION: Downtime for user-service)
docker-compose stop postgres

# Update docker-compose.yml
services:
  postgres:
    # ... existing config ...
    deploy:
      resources:
        limits:
          cpus: '4.0'      # Increased from 2.0
          memory: 8G       # Increased from 4G
        reservations:
          cpus: '2.0'
          memory: 4G
    environment:
      # Tune PostgreSQL config
      - POSTGRES_INITDB_ARGS=--data-checksums --encoding=UTF8
    command: >
      postgres
        -c shared_buffers=2GB
        -c effective_cache_size=6GB
        -c maintenance_work_mem=512MB
        -c checkpoint_completion_target=0.9
        -c wal_buffers=16MB
        -c default_statistics_target=100
        -c random_page_cost=1.1
        -c effective_io_concurrency=200
        -c work_mem=5242kB
        -c min_wal_size=1GB
        -c max_wal_size=4GB
        -c max_connections=200

# Restart PostgreSQL
docker-compose up -d postgres

# Verify startup
docker logs chat4all-postgres --tail=100 | grep "ready to accept connections"
```

**Read Replicas** (production - requires replication setup):

```yaml
# docker-compose.yml
services:
  postgres-primary:
    image: postgres:16
    # ... primary config ...

  postgres-replica-1:
    image: postgres:16
    environment:
      POSTGRES_PRIMARY_HOST: postgres-primary
      POSTGRES_REPLICATION_MODE: slave
    # ... replica config ...
```

Update user-service to use replica for read queries:
```yaml
# application.yml
spring:
  datasource:
    hikari:
      primary:
        jdbc-url: jdbc:postgresql://postgres-primary:5432/chat4all
        read-only: false
      replica:
        jdbc-url: jdbc:postgresql://postgres-replica-1:5432/chat4all
        read-only: true
```

### 3.4 Redis Scaling

**Symptoms**:
- Memory usage >80%
- Cache evictions increasing
- Slow cache operations (>10ms)

**Increase Memory Limit**:

```bash
# Update docker-compose.yml
services:
  redis:
    # ... existing config ...
    deploy:
      resources:
        limits:
          memory: 4G       # Increased from 2G
        reservations:
          memory: 2G
    command: >
      redis-server
        --maxmemory 3.5gb
        --maxmemory-policy allkeys-lru
        --save ""
        --appendonly yes

# Restart Redis
docker-compose restart redis

# Verify config
docker exec -it chat4all-redis redis-cli CONFIG GET maxmemory
```

**Redis Cluster** (production - for massive scale):

```yaml
# Requires 6 nodes minimum (3 masters, 3 replicas)
services:
  redis-node-1:
    image: redis:7-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf
    # ... config ...

  # ... redis-node-2 through redis-node-6 ...
```

---

## Scenario 4: Auto-Scaling (Future State)

### Docker Swarm Mode (Simple Auto-Scaling)

```yaml
# docker-stack.yml (for Docker Swarm)
version: '3.8'
services:
  api-gateway:
    image: chat4all/api-gateway:latest
    deploy:
      replicas: 2
      update_config:
        parallelism: 1
        delay: 10s
      restart_policy:
        condition: on-failure
        max_attempts: 3
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
      # Auto-scaling based on CPU (requires Docker Swarm + monitoring)
      # Note: Use external orchestration for true auto-scaling
```

Deploy to swarm:
```bash
docker swarm init
docker stack deploy -c docker-stack.yml chat4all
```

### Kubernetes Horizontal Pod Autoscaler (Production Recommendation)

```yaml
# k8s/api-gateway-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50        # Scale up by 50% of current
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Pods
        value: 1         # Scale down 1 pod at a time
        periodSeconds: 120
```

---

## Scaling Decision Matrix

| Symptom | Root Cause | Scaling Action | Priority |
|---------|-----------|----------------|----------|
| High API latency (P95 >2s) | API Gateway overloaded | Scale api-gateway to 3+ instances | **P0** |
| Kafka consumer lag >1000 | Message processing slow | Scale message-service, router-service | **P0** |
| High error rate (>1%) | Service crashes under load | Scale affected service + check logs | **P0** |
| External API rate limits | Connector overload | Scale connectors + implement backoff | **P1** |
| Slow database queries | MongoDB/PostgreSQL load | Vertical scale DB or add replicas | **P1** |
| Memory pressure | Redis cache full | Increase Redis memory or tune eviction | **P2** |
| Disk I/O saturation | Kafka/MongoDB disk writes | Add volumes, scale brokers/shards | **P2** |

---

## Post-Scaling Validation

### Checklist

```bash
# 1. Verify all services are running
docker-compose ps

# 2. Check for errors in logs
docker-compose logs --since 10m | grep -i "error\|exception\|fatal"

# 3. Validate load balancing
# Make 100 requests and verify distribution
for i in {1..100}; do
  curl -s http://localhost:8080/actuator/health > /dev/null
done

# Check which containers handled requests (look for log entries)
docker stats --no-stream --format "{{.Name}}\t{{.NetIO}}"

# 4. Verify Kafka consumer groups rebalanced
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group message-service-group \
  --describe

# 5. Test end-to-end functionality
curl -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "conversation_id": "test-conv-123",
    "content": "Test message after scaling",
    "content_type": "TEXT"
  }'

# 6. Monitor metrics for 15 minutes
# Check Grafana: http://localhost:3000/d/chat4all-performance
# Verify P95 latency, error rate, throughput
```

### Performance Benchmarks

**Expected metrics after scaling:**
- API Gateway P95 latency: <500ms
- Message delivery P95: <2 seconds
- Error rate: <0.1%
- Kafka consumer lag: <100 messages
- Database connection pool utilization: <70%
- Memory utilization: <80% per service

---

## Rollback Procedures

### Emergency Rollback (if scaling causes issues)

```bash
# 1. Revert to previous scale (1 instance per service)
docker-compose up -d \
  --scale api-gateway=1 \
  --scale message-service=1 \
  --scale whatsapp-connector=1 \
  --scale telegram-connector=1 \
  --scale instagram-connector=1 \
  --scale router-service=1

# 2. Wait for services to stabilize
sleep 30

# 3. Check health
docker-compose ps
docker-compose logs --tail=100 | grep "Started"

# 4. Verify functionality
curl http://localhost:8080/actuator/health

# 5. Investigate root cause
docker-compose logs --since 30m | grep -i "error\|exception"
```

### Gradual Scale-Down (after traffic returns to normal)

```bash
# Monitor traffic for 15 minutes to confirm it's stable
watch -n 60 'docker stats --no-stream --format "{{.Name}}\t{{.CPUPerc}}"'

# Scale down 1 instance at a time, wait 5 minutes between steps
docker-compose up -d --scale api-gateway=2
sleep 300

docker-compose up -d --scale api-gateway=1
sleep 300

# Repeat for other services
docker-compose up -d --scale message-service=3
sleep 300

docker-compose up -d --scale message-service=1
```

---

## Cost Optimization

### Resource Right-Sizing

```bash
# Analyze resource usage over 7 days
# Export metrics from Grafana or use docker stats logs

# Example: API Gateway uses avg 30% CPU, max 60% CPU
# Current: 1 CPU limit → Right-sized: 0.75 CPU limit

# Update docker-compose.yml
services:
  api-gateway:
    deploy:
      resources:
        limits:
          cpus: '0.75'      # Reduced from 1.0
          memory: 768M      # Reduced from 1G
```

### Idle Service Shutdown

```bash
# Stop unused connectors (e.g., Instagram if no users)
docker-compose stop instagram-connector

# Reduce router-service replicas during low traffic hours
# Schedule with cron:
# 0 2 * * * docker-compose up -d --scale router-service=1  # 2 AM
# 0 8 * * * docker-compose up -d --scale router-service=2  # 8 AM
```

---

## Monitoring & Alerting

### Key Metrics to Monitor

```bash
# CPU usage per service
docker stats --no-stream --format "{{.Name}}\t{{.CPUPerc}}" | \
  awk -F'\t' '$2+0 > 70 {print "ALERT: " $0}'

# Memory usage per service
docker stats --no-stream --format "{{.Name}}\t{{.MemUsage}}" | \
  awk -F'/' '$1+0 > 0.8*$2 {print "ALERT: " $0}'

# Disk usage
df -h | awk '$5+0 > 80 {print "ALERT: Disk usage high on " $6 ": " $5}'

# Container health
docker ps --filter health=unhealthy --format "{{.Names}}"
```

### Recommended Alerts

1. **CPU >70% for 5 minutes** → Warning, consider scaling
2. **CPU >90% for 2 minutes** → Critical, scale immediately
3. **Memory >80% for 5 minutes** → Warning, consider scaling
4. **Memory >95%** → Critical, OOM kill imminent
5. **Kafka consumer lag >1000** → Warning, scale consumers
6. **Error rate >1%** → Critical, investigate immediately
7. **P95 latency >2s** → Warning, user experience degraded

---

## Related Documentation

- [Message Delivery Failure Runbook](./message-delivery-failure.md)
- [Architecture Plan](../../specs/001-unified-messaging-platform/plan.md)
- [Performance Requirements](../../specs/001-unified-messaging-platform/spec.md#non-functional-requirements)
- [Docker Compose Reference](../../docker-compose.yml)

---

## Appendix: Capacity Planning

### Traffic Estimation

**Formula**:
```
Required Instances = (Peak Traffic / Instance Capacity) * Safety Factor

Where:
- Instance Capacity = Throughput at 70% CPU (tested via load testing)
- Safety Factor = 1.5 (50% headroom for spikes)
```

**Example**:
```
API Gateway:
- Tested capacity: 500 req/min at 70% CPU
- Peak traffic: 2000 req/min
- Required instances: (2000 / 500) * 1.5 = 6 instances

Message Service:
- Tested capacity: 1000 msg/min at 70% CPU
- Peak traffic: 5000 msg/min
- Required instances: (5000 / 1000) * 1.5 = 7.5 → 8 instances
```

### Resource Requirements

**Per Service Instance** (baseline):
- API Gateway: 0.5 CPU, 512MB RAM
- Message Service: 1 CPU, 1GB RAM
- Router Service: 0.75 CPU, 768MB RAM
- Connectors: 0.5 CPU, 512MB RAM each
- File Service: 1 CPU, 1GB RAM

**Infrastructure** (shared):
- Kafka: 2 CPU, 4GB RAM (single broker)
- MongoDB: 2 CPU, 4GB RAM
- PostgreSQL: 2 CPU, 4GB RAM
- Redis: 1 CPU, 2GB RAM

**Server Sizing Example** (peak 5000 msg/min):
- Total CPUs: 32 cores (8 msg-service + 6 api-gateway + 8 infrastructure + headroom)
- Total RAM: 64GB
- Total Disk: 500GB SSD (Kafka + MongoDB + logs)

---

**Document Version**: 1.0  
**Authors**: Chat4All DevOps Team  
**Review Cycle**: Quarterly or after major scaling events
