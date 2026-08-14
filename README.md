# Task Management — Full-Stack App

A full-stack Task Management application:

- **Backend** — Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security (JWT auth), Flyway, Lombok (Maven)
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

The `tasks` and `users` tables are created by the Flyway migrations in
`src/main/resources/db/migration/` — no manual SQL needed.
Just make sure MySQL is running.

## Run the Backend (port 8080)

Two environment variables are required:

| Variable      | Purpose |
|---------------|---------|
| `DB_PASSWORD` | MySQL password for the `root` user |
| `JWT_SECRET`  | HMAC key used to sign auth tokens — at least 32 characters (generate one with `openssl rand -base64 48`) |

```bash
export DB_PASSWORD=your_mysql_password
export JWT_SECRET=$(openssl rand -base64 48)
./mvnw spring-boot:run
```

In IntelliJ, add both under Run Configuration → Environment variables instead.
The app fails fast at startup if either is missing.

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

## Authentication

All `/api/tasks` endpoints require a JWT. Obtain one from the auth endpoints
(no token needed for register/login):

| Method | Path                      | Description | Success |
|--------|---------------------------|-------------|---------|
| POST   | `/api/auth/register`      | Create an account, returns a token | 201 (400 validation, 409 duplicate) |
| POST   | `/api/auth/login`         | Exchange credentials for a token   | 200 (401 bad credentials) |
| POST   | `/api/auth/token/refresh` | Issue a fresh token (requires a valid token) | 200 |

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
- The React app stores the session in `localStorage` and logs you out automatically when the token expires or the API returns 401.
- In Swagger UI, click **Authorize** and paste the token to call protected endpoints.

## Run the Tests

Unit tests (Mockito — no database or Docker required):

```bash
./mvnw test -Dtest="TaskServiceImplTest,TaskControllerTest,AuthServiceImplTest,AuthControllerTest,TokenControllerTest"
```

- `TaskServiceImplTest` — 8 tests for the task service layer (mocked repository)
- `TaskControllerTest` — 9 tests for the task REST layer (standalone MockMvc, mocked service)
- `AuthServiceImplTest` — 7 tests for register/login/refresh logic (mocked repository, encoder, auth manager)
- `AuthControllerTest` — 7 tests for the auth REST layer (validation, 401, 409)
- `TokenControllerTest` — 2 tests for the token refresh endpoint

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

| Method | Path              | Description        | Success |
|--------|-------------------|--------------------|---------|
| GET    | `/api/tasks`      | List all tasks     | 200 |
| GET    | `/api/tasks/{id}` | Get one task       | 200 (404 if missing) |
| POST   | `/api/tasks`      | Create a task      | 201 (400 on validation error) |
| PUT    | `/api/tasks/{id}` | Update a task      | 200 (404 if missing) |
| DELETE | `/api/tasks/{id}` | Delete a task      | 204 (404 if missing) |

Example payload:

```json
{
  "title": "Ship the release",
  "description": "Optional details",
  "status": "TODO"
}
```

`status` must be one of `TODO`, `IN_PROGRESS`, `DONE`.

## Project Structure

```
├── src/main/java/dev/anand/claudeskills/
│   ├── controller/   TaskController, AuthController (register/login), TokenController (refresh)
│   ├── service/      TaskService(Impl), AuthService(Impl), JwtService, AppUserDetailsService
│   ├── repository/   TaskRepository, UserRepository (Spring Data JPA)
│   ├── entity/       Task, User (JPA entities + Jakarta validation)
│   ├── dto/          RegisterRequest, LoginRequest, AuthResponse
│   ├── config/       SecurityConfig (JWT resource server, CORS), JwtProperties, OpenApiConfig
│   └── exception/    TaskNotFoundException, DuplicateResourceException, GlobalExceptionHandler
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/ Flyway SQL migrations (V1 tasks, V2 users)
├── src/test/java/    Mockito unit tests + Testcontainers integration test
└── frontend/         React + Vite + Tailwind GUI
    └── src/
        ├── api/        client.ts (fetch + Bearer token), taskApi.ts, authApi.ts
        ├── auth/       AuthContext.tsx, session.ts (localStorage session)
        ├── components/ AuthPage (sign in/up), TaskForm, TaskList
        └── types/      task.ts, auth.ts
```

----------------------------------------------------------------


--------------------------------------------------------------------------------------------------------------
/opt/homebrew/bin/claude
--------------------------------------------------------------------------------------------------------------

⏺ Everything works end to end. Both servers are running and every operation passed:

┌───────────────────────────┬────────────────────────────────────────────────────────────────────┐
│           Check           │                               Result                               │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ GET /api/tasks            │ 200, returns [] then the created task                              │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ POST create               │ 201, returned {"id":1, "title":"Test integration", ...}            │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ POST blank title          │ 400 with {"title":"Title is required"} — validation working        │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ CORS preflight from :5173 │ 200 with access-control-allow-origin: http://localhost:5173        │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ PUT status → DONE         │ 200, updated entity returned                                       │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ DELETE                    │ 204, and a follow-up GET correctly returns 404 with the error JSON │
├───────────────────────────┼────────────────────────────────────────────────────────────────────┤
│ Vite dev server           │ Serving on http://localhost:5173                                   │
└───────────────────────────┴────────────────────────────────────────────────────────────────────┘

Flyway ran the V1 migration against javadb on startup, and the test task I created was deleted afterward, so the database is clean.

I've left both servers running in the background — open http://localhost:5173 in your browser to see the dashboard talking to the live API. Backend logs
are in /tmp/backend.log, frontend logs in /tmp/frontend.log. Say the word if you want me to stop them.

API docs are live. Springdoc 2.8.6 works fine with your Boot 4.1 setup — both endpoints return 200 and the spec correctly documents all the /api/tasks
operations.

Your API documentation URLs:

- Swagger UI (interactive): http://localhost:8080/swagger-ui.html
- Raw OpenAPI 3.1 spec (JSON): http://localhost:8080/v3/api-docs
 --------------------------------------------------------------------------------------------------------------

 --------------------------------------------------------------------------------------------------------------
⏺ All 17 tests passed — 0 failures, 0 errors. BUILD SUCCESS.

TaskControllerTest — 9/9 passed (0.78s)
│ 0      │
┌─────┬──────────────────────────────────────────────────────────┬─────────┬────────┐
│  #  │                           Test                           │ Result │ 0 Time │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 1   │ getAllTasks_returns200WithTaskList                       │ ✅  PASS │ 0.014s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 2   │ getTaskById_whenTaskExists_returns200WithTask            │ ✅  PASS │ 0.006s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 3   │ getTaskById_whenTaskMissing_returns404WithErrorBody      │ ✅  PASS │ 0.006s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 4   │ createTask_withValidBody_returns201WithCreatedTask       │ ✅  PASS │ 0.010s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 5   │ createTask_withBlankTitle_returns400AndNeverCallsService │ ✅  PASS │ 0.016s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 6   │ createTask_withInvalidStatus_returns400                  │ ✅  PASS │ 0.640s │
├─────┼──────────────────────────────────────────────────────────┼─────────┼────────┤
│ 7   │ updateTask_withValidBody_returns200WithUpdatedTask       │ ✅  PASS │ 0.010s │
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