## Deploy Checklist: Preprod fix
**Environment:** Staging | **Date:** 2026-08-31 (Mon) | **Deployer:** Anand

### Pre-Deploy
- [ ] All tests passing in CI
- [ ] Code reviewed and approved
- [ ] No known critical bugs in release
- [ ] Database migration tested on a staging copy of prod data (backward-compatible / reversible)
- [ ] Migration rollback script written and tested
- [ ] Feature flags created and default state confirmed (off unless intentionally on)
- [ ] Flag targeting/rollout plan documented (who sees it, % rollout)
- [ ] Rollback plan documented
- [ ] On-call team notified

### Deploy
- [ ] Run database migration, verify schema/data as expected
- [ ] Deploy code to staging, verify boot and health checks
- [ ] Run smoke tests
- [ ] Toggle feature flag(s) per rollout plan
- [ ] Monitor error rates and latency for 15 min
- [ ] Verify key user flows behind the flag (on and off states)

### Post-Deploy
- [ ] Confirm metrics are nominal
- [ ] Update release notes / changelog
- [ ] Notify stakeholders
- [ ] Close related tickets

### Rollback Triggers
- Error rate exceeds [X]% *(placeholder — no live threshold available)*
- P50 latency exceeds [X]ms *(placeholder)*
- Migration causes data integrity errors → roll back migration first, then code
- Feature flag causes critical flow failure → flip flag off before any code rollback

Note: GitHub and Datadog aren't authorized in this session, so I couldn't pull live PR/CI status or pre-fill real rollback thresholds — connect them via your connector settings if you want that automated next time.