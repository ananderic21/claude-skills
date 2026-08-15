# System Design: Rate Limiter

**Date:** 2026-08-15
**Author:** Drafted with Claude for Anand
**Status:** Draft for review

## 1. Requirements

### Functional
- Limit the number of requests a client (API key, user, or IP) can make within a time window.
- Support different limits per tier (e.g. free vs. paid plans) and per endpoint (some endpoints are more expensive than others).
- Return a clear signal to the client when they're rate-limited, including how long until they can retry.
- Allow limits to be configured/updated without a code deploy.

### Non-functional
- **Scale:** growing product — assume tens to low hundreds of requests/second across the API at launch, growing over the next year. The limiter must not become the bottleneck.
- **Latency:** the rate-limit check must add negligible overhead (single-digit milliseconds) to every request, since it sits on the hot path.
- **Accuracy:** approximate limits are acceptable (slight overshoot under race conditions is fine); the goal is protecting the system, not perfect precision.
- **Availability:** if the rate limiter's backing store has a hiccup, the system should fail open (allow requests) rather than fail closed (block all traffic) — an outage in the limiter shouldn't take down the whole API.

### Constraints
- Small team, no established stack yet. Favor a managed, low-maintenance store (e.g. managed Redis) over running custom infrastructure.

## 2. High-level design

```
   Client request
        |
        v
+--------------------+
|  API Gateway /      |
|  Middleware layer    |---- rate limit check ---->  +-----------+
+--------------------+                                |  Redis    |
        |                                              |  (counts) |
   allowed? --- yes ---> forward to backend            +-----------+
        |
       no
        |
        v
   429 Too Many Requests
   + Retry-After header
```

The rate limiter is implemented as middleware sitting in front of (or inside) the API gateway, checked on every request before it reaches the application. A single shared Redis instance (or managed equivalent) stores counters, so all API instances see a consistent view of each client's usage — critical once you're running more than one API server.

## 3. Algorithm choice

**Recommended: sliding window counter using Redis.**

- Simpler alternatives (fixed window) allow bursts at window boundaries — a client could send 2x their limit by timing requests around the window reset. A sliding window (or sliding window log/counter hybrid) avoids this without much added cost.
- Token bucket is a strong alternative if you want to allow controlled bursts (e.g. a client that's been idle can "save up" a small burst of capacity). Given the requirement is primarily about protecting the system rather than shaping traffic precisely, sliding window counter is simpler to reason about and sufficient.

**Implementation sketch (Redis):**
```
key = "ratelimit:{client_id}:{endpoint_tier}"
INCR key
if result == 1: EXPIRE key, window_seconds
if result > limit: reject
```
This is a well-known atomic pattern (INCR + conditional EXPIRE) that avoids race conditions without needing Lua scripts for the common case. For stricter sliding-window accuracy, a Redis sorted set (ZADD/ZREMRANGEBYSCORE) storing individual request timestamps is more precise but costs more memory and CPU per check — not justified at this scale.

## 4. Data model / configuration

Rather than hardcoding limits, store them in a small config table or config service:

**rate_limit_rules**
| column | type | notes |
|---|---|---|
| tier | text | free, pro, enterprise |
| endpoint_group | text | e.g. "read", "write", "expensive" |
| limit | int | requests per window |
| window_seconds | int | |

The middleware looks up the client's tier and the endpoint's group, resolves the applicable rule, and checks against Redis. Caching this small config table in memory (refreshed every minute or on change) avoids a config lookup on every request.

## 5. API behavior

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1755280000
```

Including standard rate-limit headers (even on successful requests) lets client SDKs self-throttle proactively instead of hitting 429s repeatedly.

## 6. Scale and reliability

A single managed Redis instance handles far more than the stated scale (hundreds of req/s translates to a small number of INCR operations, well within Redis's normal throughput). No sharding or clustering is needed at this stage.

**Fail-open behavior:** if Redis is unreachable, the middleware should log the failure and allow the request rather than block it. Rate limiting exists to protect the system from abuse and overload — an unavailable rate limiter is a smaller risk than an outage that blocks all legitimate traffic.

**What breaks first as scale grows:** a single Redis instance becomes a bottleneck once request volume gets into the thousands per second, or if global (not just single-region) low latency is needed. At that point, moving to Redis Cluster or a regional-local counter with periodic sync becomes worth the complexity — not before.

**Monitoring:** track 429 rate per client/tier (a spike often signals a client bug — e.g. a retry loop without backoff — rather than genuine abuse) and Redis latency/availability.

## 7. Trade-off analysis

| Decision | Chosen | Alternative | Why |
|---|---|---|---|
| Algorithm | Sliding window counter | Token bucket | Simpler to implement and reason about; token bucket's burst-shaping isn't a stated requirement |
| Store | Managed Redis | In-memory per-instance counters | In-memory counters don't work correctly once there's more than one API server — each instance would enforce its own limit independently |
| Failure mode | Fail open | Fail closed | A rate-limiter outage should degrade gracefully, not cause a full API outage |
| Config | Small DB table, cached in memory | Hardcoded in application code | Lets limits change per tier/endpoint without a deploy — useful once pricing tiers exist |

## 8. What to revisit as the system grows

- If request volume moves into the thousands/second range, revisit single-instance Redis in favor of clustering or regional sharding.
- If abusive traffic patterns emerge that a simple per-client limit doesn't catch (e.g. distributed abuse across many API keys from one source), consider adding IP-based or fingerprint-based limits as a second layer.
- If the product expands to multiple regions, the rate limiter needs a strategy for cross-region consistency (or accept slightly looser per-region limits) — not a concern at current single-region scale.
