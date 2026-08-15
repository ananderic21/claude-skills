# System Design: Multi-tenant Billing System

**Date:** 2026-08-15
**Author:** Drafted with Claude for Anand
**Status:** Draft for review

## 1. Requirements

### Functional
- Track subscription plans per tenant (organization), including plan tier, seats, and billing cycle.
- Meter usage where pricing is usage-based (e.g. API calls, storage, seats) in addition to or instead of flat subscription fees.
- Generate invoices on a recurring schedule (monthly/annual) and on plan changes (upgrades/downgrades, proration).
- Process payments via a third-party processor (e.g. Stripe) rather than handling card data directly.
- Handle failed payments (dunning: retry schedule, notify tenant, eventually suspend).
- Provide tenants a self-service view of their current plan, usage, and invoice history.

### Non-functional
- **Scale:** growing product — low hundreds of tenants at launch, growing toward low thousands within a year. Billing correctness matters far more than raw throughput at this scale.
- **Correctness:** billing errors are costly (both financially and to trust) — the design should prioritize auditability and idempotency over raw performance.
- **Isolation:** one tenant's billing data, usage, and configuration must never leak into another's, both for correctness and for compliance.
- **Compliance:** no raw payment card data should touch your own systems — use the processor's tokenization/vault (e.g. Stripe Elements + Customer/PaymentMethod objects) to stay out of PCI DSS scope as much as possible.

### Constraints
- Small team, no established stack yet. Do not build a custom payments/invoicing engine — use a processor that provides subscription and invoicing primitives (Stripe Billing is the standard default) and build the thin layer that connects it to your product's tenant and usage model.

## 2. High-level design

```
+----------------+     usage events      +------------------+
|  Product        | --------------------> |  Usage metering   |
|  services        |                      |  service           |
+----------------+                      +------------------+
                                                   |
                                                   v
                                          +------------------+
                                          |  Billing service   |
                                          |  (tenant <-> plan   |
                                          |   <-> Stripe sync)  |
                                          +------------------+
                                             |            |
                                             v            v
                                    +---------------+  +------------------+
                                    |  Stripe        |  |  Internal DB      |
                                    |  (subscriptions,|  |  (tenant, plan,   |
                                    |   invoices,     |  |   usage records,  |
                                    |   payments)      |  |   audit log)      |
                                    +---------------+  +------------------+
                                             |
                                             v
                                    +---------------+
                                    |  Webhooks       |
                                    |  (payment result,|
                                    |   invoice events) |
                                    +---------------+
```

**Components:**
- **Usage metering service**: product services emit usage events (e.g. "tenant X made an API call") to this service, which aggregates them (per tenant, per period) for usage-based billing components.
- **Billing service**: the source of truth for which plan a tenant is on, and the layer that talks to Stripe to keep subscriptions, invoices, and usage records in sync. This is your own code, but it's thin — Stripe does the actual invoice generation, tax calculation (via Stripe Tax if needed), and payment processing.
- **Internal DB**: stores tenant-to-plan mapping, raw usage records (for your own analytics and disputes), and an audit log of every billing-affecting change (plan change, usage adjustment, manual credit).
- **Webhooks**: Stripe notifies your service of events (payment succeeded/failed, invoice finalized, subscription updated) — your service reacts to these to update tenant state (e.g. suspend access on repeated payment failure).

## 3. Data model

**tenants**
| column | type | notes |
|---|---|---|
| id | uuid | |
| name | text | |
| stripe_customer_id | text | |
| status | text | active, past_due, suspended, canceled |

**subscriptions**
| column | type | notes |
|---|---|---|
| id | uuid | |
| tenant_id | uuid | |
| stripe_subscription_id | text | |
| plan_tier | text | |
| seats | int | |
| current_period_start / end | timestamp | |

**usage_records**
| column | type | notes |
|---|---|---|
| id | uuid | |
| tenant_id | uuid | indexed — every query is scoped by tenant |
| metric | text | e.g. "api_calls", "storage_gb" |
| quantity | numeric | |
| period_start / period_end | timestamp | |
| reported_to_stripe_at | timestamp | null until synced |

**billing_audit_log**
| column | type | notes |
|---|---|---|
| id | uuid | |
| tenant_id | uuid | |
| event_type | text | plan_change, payment_failed, credit_issued, ... |
| actor | text | system or user id who triggered it |
| details | jsonb | |
| created_at | timestamp | |

Every table that holds tenant data is scoped by `tenant_id` with an index, and every query path in the billing service should require a tenant context — this is the core defense against cross-tenant data leakage, more important here than in most services because the data is financial.

## 4. API design (internal, product-facing)

```
GET /tenants/{id}/billing
→ { "plan": "pro", "seats": 12, "status": "active",
    "current_period_usage": { "api_calls": 42000 },
    "next_invoice_estimate": 249.00 }

POST /tenants/{id}/billing/plan
{ "plan": "enterprise", "seats": 25 }
→ triggers Stripe subscription update with proration

POST /usage-events
{ "tenant_id": "...", "metric": "api_calls", "quantity": 1 }
→ 202 Accepted (buffered, aggregated before reporting to Stripe)
```

Usage events are buffered and aggregated (e.g. hourly) before being reported to Stripe rather than reporting every single event — this reduces API calls to Stripe and gives you a place to catch anomalies (a sudden 100x spike in reported usage) before it turns into a surprise invoice.

## 5. Payment failure handling (dunning)

- On a failed payment webhook, mark the tenant `past_due`, notify them (via the notification service), and rely on Stripe's built-in retry schedule (or configure your own via Stripe Billing's dunning settings).
- After a configurable number of failed retries (Stripe default is a good starting point), suspend the tenant's access to paid features — but keep their data intact for a grace period rather than deleting anything.
- Every state transition (active → past_due → suspended) is written to the audit log, since billing disputes need a clear record of what happened and when.

## 6. Scale and reliability

At the stated scale (low hundreds to low thousands of tenants), Stripe Billing comfortably handles subscription and invoice volume — this is well within what a single Stripe account supports without any special architecture. The internal DB (Postgres) handles usage records at this volume without partitioning.

**Idempotency:** every write to Stripe (creating a subscription, reporting usage) should use Stripe's idempotency keys, since network retries are common and double-charging or double-reporting usage is the failure mode you most want to avoid.

**Webhook reliability:** Stripe retries webhook delivery on failure, but your handler should still be idempotent (check if you've already processed a given event ID before acting on it) since duplicate deliveries do happen.

**What breaks first as scale grows:** usage event volume is the most likely bottleneck before tenant count is — if a product feature generates usage events at high frequency (e.g. per-API-call), the metering service needs its own scaling story (batching, a queue in front of it) well before the billing logic itself becomes a bottleneck.

**Monitoring:** track webhook processing failures, payment failure rate, and any mismatch between internal usage records and what's been reported to Stripe (a reconciliation job run daily or weekly catches drift early, before it becomes a customer-facing billing dispute).

## 7. Trade-off analysis

| Decision | Chosen | Alternative | Why |
|---|---|---|---|
| Payment/invoicing engine | Stripe Billing | Build custom invoicing + a payment processor integration | Building this yourself means owning tax calculation, proration, dunning, and PCI scope — enormous effort for a small team with no clear advantage at this stage |
| Usage reporting | Buffered/aggregated | Real-time per-event reporting to Stripe | Reduces API load and gives a checkpoint to catch anomalous usage before it hits an invoice |
| Source of truth for plan | Your DB, synced to Stripe | Stripe as sole source of truth | Your product needs fast, tenant-scoped reads (e.g. "is this feature allowed on this plan") without a Stripe API call on every request; syncing to your own DB keeps that fast |
| Suspension on failure | Suspend access, retain data | Immediately delete or downgrade data | Reduces support burden and risk of accidental data loss from a transient payment issue |

## 8. What to revisit as the system grows

- If usage event volume grows significantly (e.g. per-API-call metering at high request rates), introduce a queue between product services and the metering service rather than direct synchronous calls.
- If tenant count grows into the tens of thousands, revisit the audit log table's growth and query patterns — partitioning by time period may become useful.
- If the product expands to multiple currencies or regions with different tax regimes, plan to lean on Stripe Tax rather than building tax logic in-house.
- If billing logic needs to support complex custom contracts (enterprise negotiated pricing), the current tier-based model will need a more flexible pricing-rules layer — not needed while pricing stays standardized.
