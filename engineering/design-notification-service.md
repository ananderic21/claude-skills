# System Design: Notification Service

**Date:** 2026-08-15
**Author:** Drafted with Claude for Anand
**Status:** Draft for review

## 1. Requirements

### Functional
- Send notifications across multiple channels: email, SMS, push, and in-app.
- Accept notification requests from internal services (e.g. billing, auth, product events) via an API or event stream.
- Support templated messages with variable substitution (e.g. "Hi {{name}}, your invoice is ready").
- Support user notification preferences (opt-in/opt-out per channel and category).
- Provide delivery status (sent, delivered, failed, bounced) back to the calling service or a dashboard.
- Support scheduled and immediate sends.

### Non-functional
- **Scale:** growing product — assume low thousands of notifications/day at launch, growing to tens of thousands/day within a year. Design should not need a rewrite to handle 10x growth, but should not be over-built for day one.
- **Latency:** immediate notifications should be dispatched within seconds; scheduled ones within a minute of their target time.
- **Reliability:** notifications should not be silently dropped. At-least-once delivery is acceptable; exactly-once is not required (idempotency handles duplicates downstream).
- **Availability:** the service should degrade gracefully — if one channel provider (e.g. an SMS vendor) is down, other channels keep working.

### Constraints
- Small team, no established tech stack yet. Bias toward managed services and low operational overhead over building infrastructure from scratch.
- Time to market matters more than handling hypothetical scale.

## 2. High-level design

```
                        +-------------------+
 Internal services ---> |  Notification API | ---> enqueue
 (billing, auth, etc.)  +-------------------+
                                  |
                                  v
                        +-------------------+
                        |   Message Queue    |   (e.g. SQS / Cloud Tasks)
                        +-------------------+
                                  |
                                  v
                        +-------------------+
                        |  Dispatch Worker    |
                        |  - render template  |
                        |  - check prefs       |
                        |  - route to channel  |
                        +-------------------+
                          |     |     |     |
                          v     v     v     v
                       Email  SMS   Push  In-app
                       (SES) (Twilio)(FCM) (DB + WS)
                          |     |     |
                          v     v     v
                     +-----------------------+
                     |  Delivery status store |
                     +-----------------------+
```

**Components:**
- **Notification API**: a thin HTTP endpoint (or event consumer) that validates requests, resolves the template, and enqueues a job. Keeping this thin means callers get a fast response and don't wait on downstream providers.
- **Queue**: decouples request intake from delivery. A managed queue (SQS, Cloud Tasks, or similar) avoids running your own broker, which matters for a small team.
- **Dispatch worker**: pulls jobs, checks user preferences, renders the template, and calls the right provider adapter. Runs as a small pool of stateless workers so it scales horizontally without any special handling.
- **Channel adapters**: one per provider (SES for email, Twilio for SMS, FCM/APNs for push, a WebSocket or polling endpoint for in-app). Each adapter is isolated so a failure in one doesn't affect others.
- **Delivery status store**: a table (Postgres is fine at this scale) tracking each notification's lifecycle, used for the status API and for retries.

## 3. Data model

**notifications**
| column | type | notes |
|---|---|---|
| id | uuid | primary key |
| tenant_id / user_id | uuid | recipient |
| category | text | e.g. "billing", "security", "marketing" — used for preference checks |
| channel | text | email, sms, push, in_app |
| template_id | text | |
| payload | jsonb | template variables |
| status | text | queued, sent, delivered, failed, bounced |
| scheduled_at | timestamp | null for immediate sends |
| created_at / updated_at | timestamp | |

**user_preferences**
| column | type | notes |
|---|---|---|
| user_id | uuid | |
| category | text | |
| channel | text | |
| enabled | boolean | |

Keeping category and channel as separate, indexed columns (rather than a single combined preference blob) makes it cheap to answer "is this user opted into billing emails" with a single indexed lookup at send time.

## 4. API design

```
POST /notifications
{
  "user_id": "...",
  "category": "billing",
  "template_id": "invoice_ready",
  "channels": ["email", "in_app"],
  "payload": { "invoice_url": "...", "amount": "..." },
  "scheduled_at": null
}
→ 202 Accepted { "notification_id": "..." }

GET /notifications/{id}
→ { "status": "delivered", "channel_results": [...] }
```

Returning `202 Accepted` rather than `200 OK` signals to callers that the request is queued, not yet delivered — this avoids callers assuming synchronous delivery and building fragile logic around it.

## 5. Delivery and retry logic

- Each channel adapter call is wrapped with retry-with-backoff (e.g. 3 attempts, exponential backoff) for transient failures (timeouts, 5xx from the provider).
- Permanent failures (invalid phone number, bounced email) are marked `failed` immediately, no retry.
- After max retries, the job moves to a dead-letter queue for manual inspection rather than being dropped.
- Idempotency: each notification request should include (or be assigned) an idempotency key so retried API calls from the caller don't produce duplicate sends.

## 6. Scale and reliability

At the stated scale (low thousands/day growing to tens of thousands/day), a single-region deployment with a managed queue and a small worker pool (2-3 instances, auto-scaled on queue depth) comfortably handles load. Postgres for the status store is fine well past this volume.

**What breaks first as scale grows:** the status store table will grow indefinitely if notifications are never purged — plan a retention policy (e.g. archive or delete records older than 90 days) before this becomes a problem, not after.

**Failover:** if a channel provider is down, the adapter's retries will exhaust and jobs land in the dead-letter queue rather than blocking the rest of the pipeline — other channels and other notifications continue processing normally.

**Monitoring:** track queue depth, per-channel failure rate, and dead-letter queue size. A spike in any of these is the earliest signal of a provider outage or a bad template deploy.

## 7. Trade-off analysis

| Decision | Chosen | Alternative | Why |
|---|---|---|---|
| Queue | Managed (SQS/Cloud Tasks) | Self-hosted (Kafka/RabbitMQ) | Small team, no ops capacity for a broker; managed queues are cheap at this volume and require zero maintenance |
| Status store | Postgres | Dedicated event store / analytics DB | Volume doesn't justify the complexity; Postgres is what the team likely already knows |
| Delivery guarantee | At-least-once | Exactly-once | Exactly-once adds significant complexity (dedup coordination); downstream idempotency is cheaper to build and maintain |
| Channel adapters | Separate, isolated per provider | Single unified "send" abstraction with less isolation | Isolation means one provider's outage doesn't cascade; worth the small extra code |

## 8. What to revisit as the system grows

- If notification volume crosses roughly 1M/day, revisit the single-queue design — consider partitioning by channel or priority so a burst of low-priority marketing sends doesn't delay time-sensitive security notifications.
- If more channels are added (e.g. Slack, webhooks) the adapter pattern should hold, but reconsider the API's `channels` array if per-channel configuration becomes complex enough to need its own object.
- If multi-region becomes a requirement, the queue and worker pool need region-aware routing — not needed at current scale.
