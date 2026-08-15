# Debug report: error.log (claude-skills, 2026-08-15)

## Note on the reported error text

The error text pasted in the intake form — `java.lang.IllegalStateException: boom-marker-42` — does not appear anywhere in `error.log` (1,973 lines, checked in full). Nothing in the log mentions `boom-marker-42` or `IllegalStateException` at all. I've ignored that string and analyzed what the log actually contains, which is three distinct, real issues. If you meant to paste a different snippet, share it and I'll fold it in.

The log contains three unrelated problems, not one "BOM exception":

1. Routing bug — `/api/tasks/search` matched by the `/{id}` handler (12 occurrences, 12:40–12:43) — **already fixed** in your working tree.
2. Password-reset email failing (10 occurrences, 11:38–12:06) — SMTP DNS failures and Gmail auth failures — **not fixed**, root cause is a malformed hardcoded credential.
3. App startup failure — MySQL `Access denied for user 'root'@'localhost'` (2 occurrences, 16:32–16:33) — **not fixed**, `DB_PASSWORD` env var missing or wrong.

---

## Issue 1: `MethodArgumentTypeMismatchException` on `/api/tasks/{id}` — already fixed

### Reproduction
- **Expected**: `GET /api/tasks/search?q=...` returns search results.
- **Actual**: Spring routed the request to `getTaskById(Long id)`, tried to parse `"search"` as a `Long`, and threw `MethodArgumentTypeMismatchException`, logged as an unhandled 500 by `GlobalExceptionHandler`.
- **Steps**: Hit `GET /api/tasks/search` — 12 occurrences in the log between 12:40:20 and 12:43:43.

### Root cause
`@GetMapping("/{id}")` had no constraint, so Spring's path matcher treated the literal segment `search` as a value for `{id}`, and mapping order made it win over `@GetMapping("/search")`.

### Fix
Already applied in `TaskController.java` (line 55), commit `a2cc8d4` "search api number formate exception issue fixed", committed **12:59:01** — 16 minutes after the last occurrence in the log:

```java
// {id:\d+} restricts this route to numeric ids so literal sub-paths like
// /search are never mistaken for an id (which would 500 on Long parsing).
@GetMapping("/{id:\\d+}")
public ResponseEntity<Task> getTaskById(@PathVariable Long id) {
```

`/{id:\d+}`, `PUT /{id:\d+}`, and `DELETE /{id:\d+}` all carry the same constraint. There's also a regression test in `TaskControllerTest.java` asserting `GET /api/tasks/search` hits `searchTasks`, not `getTaskById`. No action needed here — flagging for the record only.

---

## Issue 2: Password reset emails failing — open

### Reproduction
- **Expected**: `sendPasswordResetEmail` delivers a reset link via Gmail SMTP.
- **Actual**: Every attempt in the log fails, in two different ways:
  - `UnknownHostException: smtp.gmail.com` (DNS/network can't reach Gmail) — 11:38, 11:43(×2), 12:04, 12:06(×2), and more after line 454.
  - `AuthenticationFailedException: 535-5.7.8 Username and Password not accepted` (Gmail rejects the credential) — 11:51, 11:54, 11:57.
- **Steps**: Trigger a password reset for `ananderic21@gmail.com` / `anandkushwahakiet@gmail.com`.

### Root cause
Two independent problems, both in `application.properties` (added in commit `9751595`, 12:15:42, i.e. after these failures — so this file didn't even exist yet when most of the log was generated, and the credential still doesn't work now):

```properties
spring.mail.username=${MAIL_USERNAME:andxyz332@gmail.com}
spring.mail.password=${MAIL_PASSWORD:pehke7-ferSos-wojxav}
```

- **Malformed credential**: the code comment directly above this line spells out that a Gmail App Password is 16 lowercase letters in 4 groups of 4 (`abcd efgh ijkl mnop`). The hardcoded fallback `pehke7-ferSos-wojxav` is in the Apple/Keychain suggested-password shape (mixed case, hyphens, digits) — it is not a valid Gmail App Password and will always produce `535-5.7.8 BadCredentials`, regardless of environment. This matches the three `AuthenticationFailedException` entries exactly.
- **DNS failures**: `UnknownHostException: smtp.gmail.com` means the process couldn't resolve DNS at all — outbound network/DNS was unavailable in that environment at those timestamps. Separate from the credential problem; not something to fix in code.
- **Secret committed to git**: real or not, that fallback password is checked into source control in a public-looking repo. Worth rotating and removing regardless of whether it's a live credential.

### Fix
1. Generate a real Gmail App Password (Google Account → Security → 2-Step Verification → App passwords) on the account set in `spring.mail.username`, and set it via the `MAIL_PASSWORD` env var — don't rely on the fallback.
2. Remove the hardcoded fallback values for `MAIL_USERNAME` / `MAIL_PASSWORD` from `application.properties` (fail fast or fall back to a no-op mailer in dev, rather than a fake credential that always 535s).
3. Confirm outbound network/DNS access to `smtp.gmail.com:587` from wherever this runs, if the DNS failures recur outside this sandbox.

### Prevention
- Add a startup-time check (or a `/actuator/health` mail indicator) that verifies SMTP connectivity, so a bad credential surfaces immediately instead of silently failing on every reset request.
- Add a test asserting `spring.mail.password` is not the literal placeholder value, to prevent this exact class of "looks configured but isn't" bug.
- `git log -p` the credential history and rotate the password if it was ever real.

---

## Issue 3: App fails to start — MySQL access denied — open

### Reproduction
- **Expected**: App boots, Flyway migrates, `entityManagerFactory` initializes.
- **Actual**: `Access denied for user 'root'@'localhost' (using password: YES)`, SQL State `28000`, Error Code `1045`. Two occurrences: 16:32:25 and 16:33:25 (a restart attempt, same failure).
- **Steps**: Start the app with the current `DB_PASSWORD` value.

### Root cause
`application.properties`:
```properties
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}
```
`DB_PASSWORD` has no default, so the effective password sent to MySQL is either empty or wrong. MySQL's `root@localhost` account doesn't accept it — "using password: YES" means a password was sent, just not the right one.

### Fix
Set `DB_PASSWORD` to the actual local MySQL root password (`mysql -u root -p` to confirm it), or create a dedicated app user with a known password instead of using `root`.

### Prevention
- Avoid using MySQL `root` for the app connection; a scoped user makes credential rotation and audit easier.
- Fail with a clearer message: Spring's Flyway/Hibernate wrapping buries the actual `Access denied` several frames deep — a startup DB-connectivity check with a plain log line would save the same triage time next time.
