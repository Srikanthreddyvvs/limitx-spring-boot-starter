# LimitX — Dynamic Distributed Rate Limiter

A production-oriented Spring Boot Starter for Spring Cloud Gateway that provides distributed rate limiting with zero-downtime live configuration updates.

LimitX enables API rate limiting at the gateway layer before requests reach downstream services, making it suitable for microservice architectures and multi-instance gateway deployments.

---

## Features

* Distributed rate limiting using Redis
* Runtime limit updates without gateway restarts
* Supports multiple rate limiting algorithms:

  * Fixed Window
  * Sliding Window
  * Token Bucket
* Supports multiple matching scopes:

  * User
  * IP Address
  * Endpoint
  * Global
* Scope precedence resolution (most specific rule wins)
* Redis Pub/Sub based cache invalidation
* Caffeine L1 cache for low-latency rule lookups
* Standard rate limiting response headers
* Micrometer metrics support
* Spring Boot auto-configuration support
* Spring Cloud Gateway integration

---

## Architecture

```text
Client
   ↓
Spring Cloud Gateway
   ↓
LimitX Filter
   ↓
Redis (rules + counters + pub/sub)
   ↓
Downstream Services
```

---

## Supported Algorithms

| Algorithm      | Best For                        |
| -------------- | ------------------------------- |
| Fixed Window   | Simple global throttling        |
| Sliding Window | Smooth request distribution     |
| Token Bucket   | Burst-friendly traffic patterns |

---

## Supported Scopes

LimitX resolves rules using the following priority order:

```text
USER → IP → ENDPOINT → GLOBAL
```

The most specific matching rule is always selected.

---

## Prerequisites

* Java 21+
* Redis 7+
* Spring Boot 3.x
* Spring Cloud Gateway

---

## Quick Start

### 1. Install the Starter

```bash
cd limitx-spring-boot-starter
mvn clean install
```

### 2. Start Redis

```bash
docker compose up -d
```

### 3. Seed Rate Limit Rules

```bash
SET rl:config:user:user-123 '{"scope":"USER","algorithm":"TOKEN_BUCKET","limit":5,"windowSeconds":60,"refillTokens":5}'

SET rl:config:endpoint:/api/payments '{"scope":"ENDPOINT","algorithm":"SLIDING_WINDOW","limit":5,"windowSeconds":60,"refillTokens":0}'

SET rl:config:global '{"scope":"GLOBAL","algorithm":"FIXED_WINDOW","limit":10,"windowSeconds":60,"refillTokens":0}'
```

### 4. Add the Starter Dependency

```xml
<dependency>
    <groupId>com.limitx</groupId>
    <artifactId>limitx-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 5. Configure the Gateway Route

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: orders-route
          uri: http://localhost:8081
          predicates:
            - Path=/api/orders/**
          filters:
            - LimitX
```

### 6. Start Your Gateway

```bash
mvn spring-boot:run
```

---

## Response Headers

Every successful response contains standard rate limiting headers:

```text
X-RateLimit-Limit:     5
X-RateLimit-Remaining: 3
X-RateLimit-Reset:     1700000060
Retry-After:           45
```

`Retry-After` is included only when a request is rejected.

---

## HTTP 429 Response

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Retry after 45 seconds.",
  "retryAfter": 45
}
```

---

## Live Configuration Refresh

Update limits directly in Redis without restarting any gateway instance.

```bash
redis-cli SET rl:config:global \
'{"scope":"GLOBAL","algorithm":"FIXED_WINDOW","limit":100,"windowSeconds":60,"refillTokens":0}'

redis-cli PUBLISH rl:config-changes rl:config:global
```

All gateway instances immediately invalidate their local cache and start using the new configuration.

If configuration changes are performed through:

```java
RedisRuleRepository.saveRule()
```

the publish operation is performed automatically.

---

## Redis Key Schema

| Key Pattern                 | Type            | Purpose                   |
| --------------------------- | --------------- | ------------------------- |
| `rl:config:global`          | JSON String     | Global rule               |
| `rl:config:user:{userId}`   | JSON String     | Per-user rule             |
| `rl:config:ip:{ip}`         | JSON String     | Per-IP rule               |
| `rl:config:endpoint:{path}` | JSON String     | Per-endpoint rule         |
| `rl:bucket:global`          | String / Hash   | Runtime state             |
| `rl:bucket:user:{userId}`   | String / Hash   | Runtime state             |
| `rl:bucket:ip:{ip}`         | String / Hash   | Runtime state             |
| `rl:bucket:endpoint:{path}` | String / Hash   | Runtime state             |
| `rl:config-changes`         | Pub/Sub Channel | Cache invalidation events |

---

## Configuration Reference

```yaml
limitx:
  enabled: true
  key-prefix: rl

  cache:
    ttl-seconds: 30
    max-size: 10000

  refresh:
    enabled: true
    channel: rl:config-changes

  default-rule:
    scope: GLOBAL
    algorithm: FIXED_WINDOW
    limit: 1000
    window-seconds: 60
```

---

## Metrics

If Spring Boot Actuator is available, LimitX automatically publishes metrics through Micrometer.

```bash
curl http://localhost:8080/actuator/metrics/limitx.requests.allowed

curl http://localhost:8080/actuator/metrics/limitx.requests.denied
```

Metrics are tagged using the matched Redis rule key, allowing aggregation in systems such as Prometheus and Grafana.

---
