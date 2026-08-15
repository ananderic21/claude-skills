# Password reset — test plan

Audit of current coverage and gaps for the forgot-password / reset-password flow: `PasswordResetController`, `PasswordResetServiceImpl`, `EmailServiceImpl`, `PasswordResetTokenRepository`, and the corresponding `AuthPage` / `ResetPasswordPage` frontend screens.

## System under test

The flow spans five layers: the React forms (`AuthPage.tsx` forgot-mode, `ResetPasswordPage.tsx`), the REST endpoints (`POST /api/auth/forgot-password`, `POST /api/auth/reset-password`), the service (`PasswordResetServiceImpl`), the token persistence (`PasswordResetTokenRepository`, `PasswordResetToken`), and outbound email (`EmailServiceImpl`). Tokens are 32 random bytes, stored only as a SHA-256 hash, single-use, and expire after `app.password-reset.expiry-hours` (24h by default). The forgot-password endpoint always returns the same message regardless of whether the email is registered, so it cannot be used to enumerate accounts.

## Current coverage

`PasswordResetServiceImplTest` (unit, Mockito) is solid and already covers the core service logic:

- known email → invalidates old tokens, persists a new one, emails the link
- unknown email → no token saved, no email sent, no error (anti-enumeration)
- valid token → password encoded, user saved, token marked used
- unknown token → `InvalidTokenException`
- expired token → `InvalidTokenException`
- already-used token → `InvalidTokenException`

Everything else in the flow — the controller, the repository's custom query, the email content/failure handling, and the entire frontend — has no automated tests.

## Gaps, by layer

**Controller (`PasswordResetController`) — no tests exist.** This is the biggest gap: the HTTP contract (status codes, validation, error shape) is unverified. `AuthControllerTest` and `TaskControllerTest` already establish the pattern to copy (MockMvc `standaloneSetup` + `GlobalExceptionHandler`).

**Repository (`PasswordResetTokenRepository`) — the custom `@Modifying` JPQL (`invalidateActiveTokens`) has no persistence test.** Hand-written JPQL is exactly the kind of code that silently breaks (wrong `WHERE` clause, wrong flag) and is only caught by a real query execution. `TaskRepositorySearchTest` plus `TestcontainersConfiguration` already give this repo a working `@DataJpaTest`/Testcontainers pattern to reuse.

**Entity (`PasswordResetToken`) — `isExpired()`/`isUsable()` are only exercised indirectly** through the service tests, not at their own boundary conditions.

**Email (`EmailServiceImpl`) — zero tests.** Two things matter here: that the reset link and expiry hours actually land in both the plain-text and HTML bodies, and — more importantly — that a `MessagingException`/`MailException` is swallowed and logged rather than propagated. That swallow-and-log behavior is a security property (a mail outage must not change the controller's response and leak account existence), not just an implementation detail, so it deserves an explicit test.

**Security boundary — no rate limiting on either endpoint.** `/api/auth/forgot-password` and `/api/auth/reset-password` are `permitAll()` with no throttling. Without a limiter, forgot-password can be hammered to email-bomb a target address, and reset-password can be brute-forced against a captured token. This is a design gap as much as a test gap — flagging it because "abuse resistance" is normally something this test plan would verify, and today there's nothing to verify.

**Frontend — no test runner configured at all** (no vitest/jest in `frontend/package.json`, no `*.test.*` files anywhere in the repo). `AuthPage.tsx`'s forgot-mode and `ResetPasswordPage.tsx` both contain real client-side logic (email regex, 8-character minimum, password-confirmation match) that can regress silently.

**End-to-end** — no test drives the full loop (request reset → capture link → submit new password → log in with it), even though `ErrorLoggingEndToEndTest.java` shows this repo already has a pattern for full Spring context tests.

**Aside, not a test gap:** `application.properties` has a live-looking Gmail app password committed as the default for `spring.mail.password`. Worth rotating/removing regardless of the testing work.

## Test plan

### 1. `PasswordResetControllerTest` (new) — unit, MockMvc standalone

| # | Case | Expected |
|---|---|---|
| 1 | `POST /forgot-password` with a syntactically valid email | 200, generic "if an account exists…" message, `requestReset` called once |
| 2 | `POST /forgot-password` with blank email | 400, field error on `email` |
| 3 | `POST /forgot-password` with malformed email (`not-an-email`) | 400, field error on `email` |
| 4 | `POST /forgot-password` with email > 100 chars | 400, field error on `email` |
| 5 | `POST /reset-password` with valid token + valid password | 200, success message, `resetPassword` called with both args |
| 6 | `POST /reset-password` with blank token | 400, field error on `token` |
| 7 | `POST /reset-password` with password < 8 chars | 400, field error on `newPassword` |
| 8 | `POST /reset-password` with password > 72 chars | 400, field error on `newPassword` |
| 9 | `POST /reset-password` where service throws `InvalidTokenException` | 400, `{"error": "..."}` body matching `GlobalExceptionHandler.handleInvalidToken` |
| 10 | Response body for `/forgot-password` is identical whether or not the email exists | same JSON both times (regression guard against reintroducing enumeration) |

Coverage target: 100% of the two endpoint methods, every validation constraint on both DTOs, and the `InvalidTokenException` mapping.

### 2. `PasswordResetServiceImplTest` (existing — extend)

| # | Case | Expected |
|---|---|---|
| 1 | Token valid/unused/unexpired, but `userId` no longer resolves (user deleted after token issued) | `InvalidTokenException`, not an unhandled `NoSuchElementException`/NPE |
| 2 | `requestReset` ordering | `invalidateActiveTokens` called before `save` (InOrder verify) — guards against a future refactor breaking "new request supersedes old" |

Coverage target: keep the 6 existing cases, add the 2 above — closes the only two untested branches in the impl.

### 3. `PasswordResetTokenRepositoryTest` (new) — `@DataJpaTest` / Testcontainers, matching `TaskRepositorySearchTest`

| # | Case | Expected |
|---|---|---|
| 1 | User has one active token, `invalidateActiveTokens(userId)` called | that token's `used` flips to `true` |
| 2 | User has an already-used token and a fresh active token | only the active one flips; the already-used one is untouched (no-op, not an error) |
| 3 | `findByTokenHash` with a hash that exists | returns the row |
| 4 | `findByTokenHash` with a hash that doesn't exist | empty `Optional` |
| 5 | Two tokens with the same `token_hash` | second insert violates the unique constraint (`uk_prt_token_hash`) |

Coverage target: the custom `@Modifying` query and the unique constraint from `V4__create_password_reset_tokens_table.sql` — both invisible to the pure-mock service tests.

### 4. `PasswordResetTokenTest` (new) — unit, no mocks

| # | Case | Expected |
|---|---|---|
| 1 | `expiresAt` in the future, `used=false` | `isUsable()` true |
| 2 | `expiresAt` one second in the past | `isExpired()` true, `isUsable()` false |
| 3 | `expiresAt` in the future, `used=true` | `isUsable()` false |

Coverage target: both boundary branches of `isExpired()`/`isUsable()` directly, independent of the service.

### 5. `EmailServiceImplTest` (new) — unit, mock `JavaMailSender`

| # | Case | Expected |
|---|---|---|
| 1 | Successful send | `mailSender.send(...)` invoked once; captured message has correct `from`, `to`, subject; plain + HTML bodies both contain the reset link and expiry hours |
| 2 | `mailSender.send(...)` throws `MailException` | exception is caught, not propagated to the caller — this is the property the interface Javadoc promises |
| 3 | `createMimeMessage()`/helper construction throws `MessagingException` | same — caught, logged, no propagation |

Coverage target: both the happy path and the swallow-on-failure contract, since callers (the service, and transitively the controller) rely on this method never throwing.

### 6. Frontend — set up a runner, then component tests

No framework is installed yet; adding Vitest + React Testing Library (already the natural fit for this Vite/React app) is a prerequisite.

`AuthPage.test.tsx` (forgot mode):
- switching to "forgot" mode hides username/password fields, shows only email
- submitting with an invalid email shows the inline validation error and does not call the API
- submitting with a valid email calls `forgotPassword` and renders the returned message
- API rejection surfaces the error message, not a crash

`ResetPasswordPage.test.tsx`:
- password < 8 chars → inline error, `resetPassword` not called
- password/confirm mismatch → inline error, `resetPassword` not called
- matching valid passwords → `resetPassword` called with `(token, password)`, success view rendered
- API rejection surfaces the error message and keeps the form visible

Coverage target: the client-side validation branches in both components, since they duplicate rules the backend also enforces and can drift from them unnoticed.

### 7. End-to-end (optional, higher value once the above lands)

One Spring Boot integration test, following `ErrorLoggingEndToEndTest.java`'s pattern: register a user → call `/forgot-password` → pull the raw token out of the persisted (test) email or a test hook → call `/reset-password` → log in with the new password and confirm the old one is rejected. This is the only test that would catch a break in how the pieces wire together end to end.

## Summary of priority

1. Controller tests (section 1) — currently the least-tested layer and the one users actually hit.
2. Email swallow-on-failure test (section 5, case 2/3) — protects a stated security property.
3. Repository test for the custom query (section 3) — protects against a silent JPQL bug.
4. Frontend runner + component tests (section 6) — currently zero coverage on either screen.
5. Service edge case + entity boundary tests (sections 2, 4) — small, cheap, close remaining branches.
6. Rate limiting — not a test to write yet, a decision to make; once there's a limiter, test it.
7. E2E (section 7) — nice-to-have once the layers above are individually solid.
