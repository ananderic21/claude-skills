# Task Management — Full-Stack App

A full-stack Task Management application:

- **Backend** — Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security (JWT auth), Flyway, Spring Mail (Gmail SMTP), Lombok (Maven)
- **Frontend** — React 19, TypeScript, Vite, Tailwind CSS v4
- **Database** — MySQL 8

## Prerequisites

| Tool  | Version | Check           |
|-------|---------|-----------------|
| JDK   | 21+     | `java -version` |
| Node  | 20+     | `node -v`       |
| MySQL | 8+      | running on port 3306 |

## Database

The backend connects to MySQL with these settings (see `src/main/resources/application.properties`):

| Property | Value |
|----------|-------|
| URL      | `jdbc:mysql://localhost:3306/javadb` |
| Username | `root` |
| Password | read from the `DB_PASSWORD` environment variable |
| Schema   | `javadb` — created automatically on first run (`createDatabaseIfNotExist=true`) |

The `tasks`, `users`, and `password_reset_tokens` tables are created by the
Flyway migrations in `src/main/resources/db/migration/` — no manual SQL needed.
Just make sure MySQL is running.

## Run the Backend (port 8080)

Two environment variables are **required**:

| Variable      | Purpose |
|---------------|---------|
| `DB_PASSWORD` | MySQL password for the `root` user |
| `JWT_SECRET`  | HMAC key used to sign auth tokens — at least 32 characters (generate one with `openssl rand -base64 48`) |

Two more are **optional** — needed only for the "forgot password" email (see
[Password Reset Email](#password-reset-email-gmail-smtp)). If unset, the app
still boots and only the reset-email send fails (logged to `logs/error.log`):

| Variable        | Purpose |
|-----------------|---------|
| `MAIL_USERNAME` | Gmail address that sends reset emails |
| `MAIL_PASSWORD` | Gmail **App Password** (16 letters) for that account — *not* your normal password |

```bash
export DB_PASSWORD=your_mysql_password
export JWT_SECRET=$(openssl rand -base64 48)
export MAIL_USERNAME=youraccount@gmail.com   # optional
export MAIL_PASSWORD=abcdefghijklmnop         # optional (Gmail App Password)
./mvnw spring-boot:run
```

In IntelliJ, add these under Run Configuration → Environment variables instead.
The app fails fast at startup if `DB_PASSWORD` or `JWT_SECRET` is missing.

## Run the Frontend / GUI (port 5173)

```bash
cd frontend
npm install        # first time only
npm run dev
```

Open **http://localhost:5173** — you'll land on the sign-in page. Create an
account (or sign in), then the Task Dashboard lets you create tasks, change
their status (To Do / In Progress / Done), and delete them. Use **Sign out**
in the header to end the session.

Forgot your password? Click **Forgot your password?** on the sign-in page,
enter your email, and you'll receive a reset link. The link opens
`/reset-password?token=…` where you choose a new password (see
[Password Reset Email](#password-reset-email-gmail-smtp)).

## Authentication

All `/api/tasks` endpoints require a JWT. Obtain one from the auth endpoints
(no token needed for register/login):

| Method | Path                      | Description | Success |
|--------|---------------------------|-------------|---------|
| POST   | `/api/auth/register`      | Create an account, returns a token | 201 (400 validation, 409 duplicate) |
| POST   | `/api/auth/login`         | Exchange credentials for a token   | 200 (401 bad credentials) |
| POST   | `/api/auth/token/refresh` | Issue a fresh token (requires a valid token) | 200 |
| POST   | `/api/auth/logout`        | Record the logout time (requires a valid token) | 204 |
| POST   | `/api/auth/forgot-password` | Email a password reset link (no token needed) | 200 (always, even if the email is unknown) |
| POST   | `/api/auth/reset-password`  | Set a new password using the emailed token | 200 (400 if the token is invalid/expired) |

## User Profile

All `/api/profile` endpoints require a valid token and operate on the
authenticated user:

| Method | Path                    | Description | Success |
|--------|-------------------------|-------------|---------|
| GET    | `/api/profile`          | Current user's profile (username, name, email, picture flag) | 200 |
| PUT    | `/api/profile`          | Update name, username, email — username/email must stay unique | 200 (400 validation, 409 duplicate) |
| PUT    | `/api/profile/password` | Change password (requires the current password) | 204 (400 wrong/invalid password) |
| POST   | `/api/profile/picture`  | Upload a profile picture (multipart `file`: JPEG/PNG/WebP, max 2MB) | 200 (400 bad type, 413 too large) |
| GET    | `/api/profile/picture`  | Serve the stored profile picture | 200 (404 if none) |

Notes:

- The JWT subject is the username, so changing it returns a fresh token in the
  response (`auth` field) — the React app switches to it automatically.
- Pictures are stored under `uploads/` (gitignored, configurable via
  `app.uploads.dir`); the filename is derived from the user id, never from
  client input.
- In the GUI, click your avatar in the header to open **My Profile** — edit
  details, change the password, or upload a photo there.

Example:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"anand","password":"your-password"}' | jq -r .token)

curl http://localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN"
```

Details:

- Register payload: `{"username", "email", "password"}` — username 3–50 chars, password 8–72 chars.
- Response: `{"token", "tokenType": "Bearer", "expiresInSeconds", "username"}`; tokens expire after 8 hours (`app.jwt.expiration-seconds`).
- Tokens are HS256-signed JWTs (Spring Security OAuth2 Resource Server); passwords are stored BCrypt-hashed in the `users` table.
- The React app stores the session in `localStorage` and logs you out automatically when the token expires or the API returns 401. **Sign out** also calls `/api/auth/logout` so the logout time lands in `logs/user_audit.log`.
- In Swagger UI, click **Authorize** and paste the token to call protected endpoints.

## Password Reset Email (Gmail SMTP)

"Forgot password" emails a single-use, 24-hour reset link over Gmail SMTP.

**Flow**

1. `POST /api/auth/forgot-password` with `{"email":"you@example.com"}`. The
   response is always the same generic message, so the endpoint can't be used to
   probe which emails are registered.
2. If the email belongs to an account, a link is emailed:
   `http://localhost:5173/reset-password?token=<random>`. Any earlier link for
   that user is invalidated so only the newest one works.
3. `POST /api/auth/reset-password` with `{"token":"…","newPassword":"…"}` sets the
   new password. The token is rejected (**400**) if it's unknown, already used,
   or older than 24 hours.

**Security**

- Only the **SHA-256 hash** of the token is stored (`password_reset_tokens`
  table) — the raw token lives only in the email link, so a DB leak can't be
  replayed.
- The link lifetime is `app.password-reset.expiry-hours` (default `24`); the
  reset URL base is `app.frontend.base-url` (default `http://localhost:5173`).
- Email is sent asynchronously (`@Async`); send failures are logged to
  `logs/error.log` and never block the HTTP response or reveal account existence.

**Gmail configuration** (in `application.properties`, values from env vars):

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:...}
spring.mail.password=${MAIL_PASSWORD:...}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

`MAIL_PASSWORD` must be a Google **App Password**, not your normal login
password:

1. Enable **2-Step Verification** on the Google account
   (Google Account → Security).
2. Create an **App Password** at <https://myaccount.google.com/apppasswords>.
3. It's **16 lowercase letters** shown as four groups (`abcd efgh ijkl mnop`) —
   enter it with the spaces removed. It must be generated on the *same* account
   set in `spring.mail.username`, or Gmail replies `535-5.7.8 BadCredentials`.

## Run the Tests

Unit tests (Mockito — no database or Docker required):

```bash
./mvnw test -Dtest="TaskServiceImplTest,TaskControllerTest,AuthServiceImplTest,AuthControllerTest,TokenControllerTest,ProfileServiceImplTest,ProfileControllerTest,PasswordResetServiceImplTest"
```

- `TaskServiceImplTest` — 8 tests for the task service layer (mocked repository)
- `TaskControllerTest` — 9 tests for the task REST layer (standalone MockMvc, mocked service)
- `AuthServiceImplTest` — 7 tests for register/login/refresh logic (mocked repository, encoder, auth manager)
- `AuthControllerTest` — 8 tests for the auth REST layer (validation, 401, 409, logout)
- `TokenControllerTest` — 2 tests for the token refresh endpoint
- `ProfileServiceImplTest` — 13 tests for profile update/uniqueness, password change, picture storage
- `ProfileControllerTest` — 11 tests for the profile REST layer (validation, 409, multipart upload, 404)
- `PasswordResetServiceImplTest` — 6 tests for the forgot/reset-password flow: token issue + email send, no leak on unknown email, successful reset, and invalid/expired/used-token rejection (mocked repositories, encoder, email)

Full suite including the Testcontainers integration test (requires Docker running):

```bash
./mvnw test
```

Test reports land in `target/surefire-reports/`.

## API Documentation (Swagger)

With the backend running:

- **Swagger UI (interactive):** http://localhost:8080/swagger-ui.html
- **OpenAPI 3.1 spec (JSON):** http://localhost:8080/v3/api-docs

## REST Endpoints

Base URL: `http://localhost:8080/api/tasks` — all require `Authorization: Bearer <token>`
(requests without a valid token get **401**).

| Method | Path              | Description                       | Success |
|--------|-------------------|-----------------------------------|---------|
| GET    | `/api/tasks`      | List all tasks (unpaged)          | 200 |
| GET    | `/api/tasks/page` | List tasks paged + filtered       | 200 |
| GET    | `/api/tasks/{id}` | Get one task                      | 200 (404 if missing) |
| POST   | `/api/tasks`      | Create a task                     | 201 (400 on validation error) |
| PUT    | `/api/tasks/{id}` | Update a task                     | 200 (404 if missing) |
| DELETE | `/api/tasks/{id}` | Delete a task                     | 204 (404 if missing) |

Example payload:

```json
{
  "title": "Ship the release",
  "description": "Optional details",
  "status": "TODO"
}
```

`status` must be one of `TODO`, `IN_PROGRESS`, `DONE`.

### Pagination & filtering

`GET /api/tasks/page` returns one page of tasks (sorted by `id`) plus the global
per-status counts, so the dashboard stat cards stay accurate independent of the
current page or filter.

| Query param | Default | Notes |
|-------------|---------|-------|
| `status`    | *(none)* | Filter by `TODO`, `IN_PROGRESS`, or `DONE`. Omit for all statuses. |
| `page`      | `0`      | Zero-based page index. |
| `size`      | `10`     | Rows per page. Clamped to a maximum of `100`. |

```bash
curl "http://localhost:8080/api/tasks/page?status=TODO&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

Response:

```json
{
  "tasks": [
    { "id": 1, "title": "Ship the release", "description": "Optional details", "status": "TODO" }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "todoCount": 1,
  "inProgressCount": 0,
  "doneCount": 0
}
```

`totalElements` / `totalPages` reflect the **filtered** result set (used to drive
the pager), while `todoCount` / `inProgressCount` / `doneCount` are always counts
across the whole table.

## Project Structure

```
├── src/main/java/dev/anand/claudeskills/
│   ├── controller/   TaskController, AuthController (register/login/logout), TokenController (refresh), ProfileController
│   ├── service/      TaskService(Impl), AuthService(Impl), ProfileService(Impl), JwtService, AppUserDetailsService
│   ├── repository/   TaskRepository, UserRepository (Spring Data JPA)
│   ├── entity/       Task, User (JPA entities + Jakarta validation)
│   ├── dto/          RegisterRequest, LoginRequest, AuthResponse, Profile* / ChangePasswordRequest
│   ├── config/       SecurityConfig (JWT resource server, CORS), JwtProperties, OpenApiConfig
│   ├── logging/      RequestLoggingAspect, AuditAspect, UserSessionAuditAspect, PerformanceAspect (Spring AOP)
│   └── exception/    TaskNotFoundException, DuplicateResourceException, GlobalExceptionHandler
├── src/main/resources/
│   ├── application.properties
│   ├── logback-spring.xml    (separate rolling log files under logs/)
│   └── db/migration/ Flyway SQL migrations (V1 tasks, V2 users, V3 profile fields)
├── src/test/java/    Mockito unit tests + Testcontainers integration test
└── frontend/         React + Vite + Tailwind GUI
    └── src/
        ├── api/        client.ts (fetch + Bearer token), taskApi.ts, authApi.ts, profileApi.ts
        ├── auth/       AuthContext.tsx, session.ts (localStorage session)
        ├── components/ AuthPage (sign in/up), TaskForm, TaskList, ProfilePage, Avatar
        └── types/      task.ts, auth.ts, profile.ts
```

## Logging

Three Spring AOP aspects (`@Around` advice in the `logging` package) write to
separate rolling files under `logs/` (gitignored; rolled daily and gzipped):

| File | Logger/Aspect | Contents |
|------|---------------|----------|
| `logs/request.log` | `RequestLoggingAspect` | Every API request/response: HTTP method, URI, authenticated user, client IP, handler, sanitized args, status or exception |
| `logs/audit.log` | `AuditAspect` | Who did what: register/login/refresh and task create/update/delete with actor, outcome SUCCESS/FAILURE, and details (kept 90 days) |
| `logs/user_audit.log` | `UserSessionAuditAspect` | User login and logout times: `event=LOGIN/LOGOUT \| user \| outcome` — failed logins are logged as WARN with the reason (kept 90 days) |
| `logs/performance.log` | `PerformanceAspect` | Time taken by each controller and service method; entries over `app.logging.slow-threshold-ms` (default 500) are logged as `WARN SLOW` |

Every request gets a short correlation id (e.g. `[1b102b63]`) shared across all
three files, so you can trace a single request end to end. Passwords and JWT
tokens are always masked (`password=***`) via the DTOs' `toString()` overrides.

----------------------------------------------------------------


--------------------------------------------------------------------------------------------------------------
/opt/homebrew/bin/claude
--------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------
#Claude Code Query
--------------------------------------------------------------------------------------------------------------
Act as an expert Senior Full-Stack Engineer specializing in Java, Spring Boot 3.x, and React 18+ (using TypeScript and Vite).

I need a complete, step-by-step boilerplate for a full-stack web application. The core feature of this application is a [INSERT YOUR APP TYPE HERE, e.g., Task Management / Employee Directory / Book Store].

Please provide the exact code, configurations, and directory structures according to the following architectural requirements:

1. BACKEND (Java & Spring Boot 3.x, Maven):
    - Project Metadata: Group ID `dev.anand`, Artifact ID `claude-skills
`, Java 21.
    - Dependencies: Spring Web, Spring Data JPA, Lombok, Validation, and [INSERT DATABASE DRIVER HERE, e.g., PostgreSQL Driver / MySQL Driver].
    - Configurations: A complete `application.properties` file configuring the database connection, Hibernate DDL auto (set to update), and server port 8080.
    - Architecture: Implement a clean 4-tier architecture. Provide complete Java code for:
        - Entity: Representing a [INSERT CORE DOMAIN object, e.g., "Task" or "Employee"] with an auto-generated ID, strings, and standard JPA annotations. Include Jakarta validation (`@NotBlank`, etc.).
        - Repository: An interface extending `JpaRepository`.
        - Service: An interface and implementation class containing business logic for basic CRUD operations.
        - Controller: A REST Controller exposing mapping endpoints (`GET`, `POST`, `PUT`, `DELETE`). Crucially, include the `@CrossOrigin(origins = "http://localhost:5173")` annotation to avoid CORS errors.

2. FRONTEND (React, Vite, TypeScript, Tailwind CSS):
    - Project Tooling: Created using Vite (`npm create vite@latest`).
    - State Management: Use standard React Hooks (`useState`, `useEffect`) to manage state cleanly.
    - API Fetching: Use native Fetch API or Axios to interact with the backend port 8080 endpoints.
    - Core Components: Provide complete TypeScript (.tsx) code for:
        - A modern responsive Dashboard UI using Tailwind CSS.
        - A Component to fetch and display the list of items from the backend in a beautiful layout/table.
        - A Form Component to add/create a new item, handling state mapping and submission payload validations.

3. INTEGRATION FLOW:
    - Provide explicit, brief terminal commands to initialize both the Spring Boot app and the Vite-React app from scratch.
    - Explain how the React frontend interacts via JSON payload with the Spring Boot Rest endpoints.

Ensure the code is robust, free of placeholders, fully functional, and ready to be compiled immediately.