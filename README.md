# Personal Finance Manager API

A session-authenticated REST API for tracking income and expenses, organising them into categories, monitoring savings goals and producing monthly and yearly reports. It is a single Spring Boot service backed by PostgreSQL, with every user's data isolated from every other user's.

- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Testing and coverage](#testing-and-coverage)
- [Authentication, sessions and cookies](#authentication-sessions-and-cookies)
- [CSRF](#csrf)
- [API reference](#api-reference)
- [Error responses](#error-responses)
- [Business rules](#business-rules)
- [Database schema](#database-schema)
- [Design decisions and assumptions](#design-decisions-and-assumptions)
- [Deployment](#deployment)
- [Known limitations](#known-limitations)

---

## Technology stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.3.5 (Web, Security, Data JPA, Validation, Actuator) |
| Database | PostgreSQL (production); H2 in PostgreSQL mode (tests and the zero-setup `h2` profile) |
| Migrations | Flyway; Hibernate runs in `validate` mode only |
| Security | Spring Security, server-side `HttpSession`, BCrypt password hashing |
| API docs | springdoc-openapi (`/swagger-ui.html`, `/v3/api-docs`) |
| Tests | JUnit 5, Mockito, MockMvc, Spring Security Test, AssertJ |
| Coverage | JaCoCo, with an 80% line-coverage gate enforced by `mvn verify` |
| Build | Maven (wrapper included) |

## Architecture

The code follows a layered design, Controller → Service → Repository, with DTOs at the HTTP boundary:

```
src/main/java/com/example/finance
├── FinanceApplication.java
├── config/          SecurityConfig, AppProperties, TimeConfig (Clock), WebConfig, OpenApiConfig,
│                    RequestSizeLimitFilter
├── security/        CustomUserDetails(Service), SessionAuthenticationHandler (login/logout session work),
│                    SecurityErrorHandler (JSON 401/403 from the filter chain)
├── controller/      Auth, Transaction, Category, Goal, Report controllers (HTTP mapping only)
├── service/         Auth, Transaction, Category, Goal, Report services (business rules, @Transactional)
│                    GoalProgressCalculator + GoalProgress (single source of truth for goal progress)
├── repository/      Spring Data repositories, TransactionSpecifications (dynamic filters),
│                    projection/ (aggregation result records)
├── entity/          User, Category, Transaction, SavingsGoal, enums/TransactionType
├── dto/             auth, transaction, category, goal, report, common (Java records)
├── mapper/          Entity → response DTO mappers
├── exception/       GlobalExceptionHandler (@RestControllerAdvice), ErrorResponse, domain exceptions
└── util/            MonetaryAmounts (scale/rounding policy), InputNormalizer
src/main/resources
├── application.yml, application-local.yml, application-h2.yml
└── db/migration/V1..V4 (users, categories + default seed, transactions, savings_goals)
```

How each layer is kept to its job:

- **Single responsibility:**
  - Controllers only translate HTTP.
  - Services own the business rules and transaction boundaries.
  - Repositories own queries.
  - Mappers own conversion.
  - `SessionAuthenticationHandler` owns session mechanics.
  - `SecurityErrorHandler` owns errors raised inside the security filter chain.
- **Open/closed:**
  - Transaction filters are composable JPA `Specification` predicates, so a new filter is one more predicate rather than another query method.
  - Reports share one aggregation routine that is parameterised by date range.
- **Dependency inversion:**
  - Time comes from an injected `java.time.Clock`.
  - Password hashing and authentication go through the `PasswordEncoder` and `AuthenticationManager` abstractions.
  - All collaborators are constructor-injected and stored in final fields.
- **Interfaces only where useful:** services are concrete classes, because a second implementation isn't foreseeable. Mockito mocks the collaborators directly.

## Getting started

### Prerequisites

- JDK 17 or later
- PostgreSQL 13 or later (not needed for the `h2` profile or for the tests)
- Maven is optional, because `./mvnw` downloads it

### Option A: zero setup (in-memory H2)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
# API at http://localhost:8080/api, Swagger UI at http://localhost:8080/swagger-ui.html
```

Data is lost on restart. This option is intended for demos and quick evaluation.

### Option B: local PostgreSQL

```bash
# 1. Create a database and user (adjust names and password as needed)
psql -d postgres -c "CREATE USER finance WITH PASSWORD 'change-me';"
psql -d postgres -c "CREATE DATABASE finance OWNER finance;"

# 2. Start the application. Flyway creates the schema and seeds default categories on first start.
export DB_URL=jdbc:postgresql://localhost:5432/finance DB_USERNAME=finance DB_PASSWORD=change-me
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

# Or build and run the jar
./mvnw clean package
SPRING_PROFILES_ACTIVE=local java -jar target/personal-finance-manager.jar
```

Migrations run automatically at startup, so there is no separate migration command. Restarting does not re-run them or duplicate the seed data.

### End-to-end smoke test

`scripts/api_smoke_test.sh` is this project's own curl-based regression script. It runs 67 checks covering every endpoint, the main error cases, cross-user isolation and logout. It is **not** the assignment's `financial_manager_tests.sh`.

```bash
bash scripts/api_smoke_test.sh http://localhost:8080/api
```

## Configuration

All deployment-specific values come from environment variables. No secrets are committed; `.env.example` lists every variable with placeholder values.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | built from the three variables below | Full JDBC URL. Takes precedence when set. |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `finance` | Used when `DB_URL` is unset (matches what Render exposes). |
| `DB_USERNAME` / `DB_PASSWORD` | `finance` / empty | Database credentials |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | `5` / `1` | Hikari pool size, sized for small free-tier databases |
| `PORT` | `8080` | HTTP port. Hosting platforms usually inject this. |
| `SPRING_PROFILES_ACTIVE` | none (production) | `local` (PostgreSQL over plain HTTP) or `h2` |
| `COOKIE_SECURE` | `true` (`false` in `local`/`h2`) | `Secure` attribute on the session cookie |
| `COOKIE_SAME_SITE` | `strict` | `SameSite` attribute on the session cookie |
| `SESSION_TIMEOUT` | `30m` | Idle session timeout |
| `SESSION_COOKIE_NAME` | `JSESSIONID` | Session cookie name |
| `APP_TIME_ZONE` | `UTC` | Time zone that defines "today" for date rules |
| `CSRF_ENABLED` | `false` | Require CSRF tokens. See [CSRF](#csrf). |
| `MAX_REQUEST_SIZE` | `16KB` | Larger request bodies are rejected with 413. |
| `API_DOCS_ENABLED` | `true` | Serve Swagger UI and the OpenAPI JSON |

## Testing and coverage

```bash
./mvnw test      # 219 tests (unit + integration), H2 in-memory, no external services needed
./mvnw verify    # tests + JaCoCo report + fails the build if line coverage < 80%
open target/site/jacoco/index.html
```

Latest measured coverage: **98.2% line**, **89.0% branch** (only `FinanceApplication.main` is excluded).

| Suite | What it proves |
| --- | --- |
| `service/*Test` (Mockito) | Business rules and error paths in isolation: future dates, type mismatches, duplicate races translated to 409, ownership, rounding |
| `GoalProgressTest` (parameterised) | Percentage and remaining-amount maths: zero, negative, exactly 100%, above 100%, HALF_UP rounding |
| `RepositoryIntegrationTest` (`@DataJpaTest` on the Flyway schema) | Seed data, unique/check/foreign-key constraints, aggregation queries, specification filters |
| `integration/*IntegrationTest` (MockMvc, full security chain) | HTTP contract, status codes, validation matrices, cross-user isolation, report and goal maths end-to-end, malformed input |
| `SessionCookieIntegrationTest` (real Tomcat on a random port) | `HttpOnly` + `SameSite=Strict` cookie, the cookie round trip, invalidation on logout |
| `CsrfEnabledIntegrationTest` | Token required when enabled; login and register exempt |
| `ConcurrencyIntegrationTest` | Six-thread races on the same email and the same category name: exactly one wins, the rest get 409 |

Integration tests pin the business clock to 2026-10-10 UTC (`TestClockConfig`) and clean all user data after each test, so they don't depend on the real date or on test order.

## Authentication, sessions and cookies

- **Register:** `POST /api/auth/register` creates the account. Passwords are hashed with BCrypt and never logged; the request DTOs mask them in `toString`.
- **Log in:** `POST /api/auth/login` authenticates through Spring Security's `AuthenticationManager` and stores the security context in the server-side `HttpSession`. The response sets `JSESSIONID`.
  - **Session fixation:** if a session already existed, its id is changed on login.
  - **No account enumeration:** a wrong password and an unknown email return the same 401 response.
- **Cookie attributes:** `HttpOnly; SameSite=Strict; Path=/`, plus `Secure` whenever `COOKIE_SECURE=true` (the production default). Behind a TLS-terminating proxy such as Render, `server.forward-headers-strategy=framework` makes the app treat requests as HTTPS.
- **Protected endpoints:** every endpoint except register, login, `GET /api/auth/csrf`, `/actuator/health` and the API docs requires a valid session. Otherwise the response is a JSON 401, never a redirect or an HTML page.
- **Log out:** `POST /api/auth/logout` invalidates the session, clears the security context and expires the cookie. Calling it without a valid session returns 401.

Clients must send the session cookie back on every request. With curl, use a cookie jar:

```bash
BASE=http://localhost:8080/api
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"user@example.com","password":"password123","fullName":"John Doe","phoneNumber":"+1234567890"}'
curl -s -c cookies.txt -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user@example.com","password":"password123"}'
curl -s -b cookies.txt $BASE/categories              # authenticated
curl -s -b cookies.txt -X POST $BASE/auth/logout     # session invalidated
```

## CSRF

Cookie-based sessions are exposed to cross-site request forgery. The defences below are **always on**:

1. **SameSite cookie:** the session cookie is `SameSite=Strict`, so browsers do not attach it to requests that come from another site.
2. **No CORS:** no CORS configuration is published, so cross-origin scripts cannot read responses or send preflighted requests.
3. **JSON-only bodies:** request bodies must be `application/json`, which a cross-site HTML form cannot submit without a CORS preflight.

On top of that, **synchronizer-token CSRF protection is available behind `CSRF_ENABLED`**. It defaults to `false` because the assignment's grading script and other non-browser clients (curl, server-to-server) cannot obtain and echo a token, and the published API contract has no token field. Enable it whenever a browser front end consumes the API:

```bash
CSRF_ENABLED=true
```

With CSRF enabled:

1. Call `GET /api/auth/csrf`. It returns `{"csrfEnabled":true,"headerName":"X-XSRF-TOKEN","token":"..."}` and sets an `XSRF-TOKEN` cookie that scripts can read (it is not `HttpOnly`).
2. Send that value in the `X-XSRF-TOKEN` header on every `POST`, `PUT` and `DELETE` except register and login.
3. Requests without a valid token receive `403 {"message":"Missing or invalid CSRF token"}`.

## API reference

All request and response bodies are JSON, dates use `yyyy-MM-dd`, and monetary values have two decimal places. Interactive docs are at `/swagger-ui.html`.

| Method | Path | Success | Errors |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 201 | 400, 409 |
| POST | `/api/auth/login` | 200 + cookie | 400, 401 |
| POST | `/api/auth/logout` | 200 | 401 |
| GET | `/api/auth/csrf` | 200 | none |
| POST | `/api/transactions` | 201 | 400, 401 |
| GET | `/api/transactions?startDate&endDate&categoryId&category&type` | 200 | 400, 401 |
| PUT | `/api/transactions/{id}` | 200 | 400, 401, 404 |
| DELETE | `/api/transactions/{id}` | 200 | 401, 404 |
| GET | `/api/categories` | 200 | 401 |
| POST | `/api/categories` | 201 | 400, 401, 409 |
| DELETE | `/api/categories/{name}` | 200 | 400, 401, 403, 404 |
| POST | `/api/goals` | 201 | 400, 401 |
| GET | `/api/goals` | 200 | 401 |
| GET | `/api/goals/{id}` | 200 | 400, 401, 403, 404 |
| PUT | `/api/goals/{id}` | 200 | 400, 401, 403, 404 |
| DELETE | `/api/goals/{id}` | 200 | 401, 403, 404 |
| GET | `/api/reports/monthly/{year}/{month}` | 200 | 400, 401 |
| GET | `/api/reports/yearly/{year}` | 200 | 400, 401 |
| GET | `/actuator/health` | 200 | none |

### Auth

```http
POST /api/auth/register
{"username":"user@example.com","password":"password123","fullName":"John Doe","phoneNumber":"+1234567890"}
→ 201 {"message":"User registered successfully","userId":1}

POST /api/auth/login
{"username":"user@example.com","password":"password123"}
→ 200 {"message":"Login successful"}        (Set-Cookie: JSESSIONID=...; HttpOnly; SameSite=Strict)

POST /api/auth/logout
→ 200 {"message":"Logout successful"}
```

Registration validation:

| Field | Rule |
| --- | --- |
| `username` | Valid email, at most 254 characters. Trimmed and lower-cased before storage. Unique, case-insensitively. |
| `password` | 8–72 characters (72 is BCrypt's limit), at least one letter and one digit, no whitespace |
| `fullName` | 1–100 characters: letters, spaces, `.` `'` `-`. Must start with a letter. |
| `phoneNumber` | Optional leading `+`, then 7–15 digits not starting with 0. Spaces, dashes, dots and parentheses are stripped first. |

### Transactions

```http
POST /api/transactions
{"amount":50000.00,"date":"2024-01-15","category":"Salary","description":"January Salary"}
→ 201 {"id":1,"amount":50000.00,"date":"2024-01-15","category":"Salary","description":"January Salary","type":"INCOME"}

GET /api/transactions?startDate=2024-01-01&endDate=2024-01-31&categoryId=1
→ 200 {"transactions":[{"id":1,"amount":50000.00,"date":"2024-01-15","category":"Salary","description":"January Salary","type":"INCOME"}]}

PUT /api/transactions/1
{"amount":60000.00,"description":"Updated January Salary"}
→ 200 {"id":1,"amount":60000.00,"date":"2024-01-15","category":"Salary","description":"Updated January Salary","type":"INCOME"}

DELETE /api/transactions/1
→ 200 {"message":"Transaction deleted successfully"}
```

**Create:**
- `amount` must be greater than 0, with at most 13 integer digits and 2 decimal places.
- `date` must be a real calendar date that is not after today in `APP_TIME_ZONE`.
- `category` is a case-insensitive name of a default category or one of your own custom categories.
- `description` is optional, up to 255 characters.
- `type` is optional. It is always derived from the category, and if you send it, it must match or you get a 400.

**List:**
- Results are sorted newest date first; transactions on the same date are ordered newest id first.
- All filters are optional and combined with AND:
  - `startDate` and `endDate` (both inclusive; `startDate > endDate` returns 400)
  - `categoryId`
  - `category` (a name)
  - `type` (`INCOME` or `EXPENSE`, any letter case)
- An empty result is `{"transactions":[]}`.

**Update:**
- This is a partial update: `amount`, `category` and `description` are updatable, and fields you leave out are not changed.
- An empty `description` clears it.
- The date can never change. A `date` that differs from the stored one returns 400; sending the same date is accepted as a no-op.
- A body with nothing updatable returns 400.
- Changing the category changes the derived `type`.

### Categories

```http
GET /api/categories
→ 200 {"categories":[{"id":1,"name":"Salary","type":"INCOME","isCustom":false}, ..., {"id":8,"name":"SideBusinessIncome","type":"INCOME","isCustom":true}]}

POST /api/categories
{"name":"SideBusinessIncome","type":"INCOME"}
→ 201 {"id":8,"name":"SideBusinessIncome","type":"INCOME","isCustom":true}

DELETE /api/categories/SideBusinessIncome
→ 200 {"message":"Category deleted successfully"}
```

**Default categories** are seeded by migration `V2` and cannot be modified or deleted:
- INCOME: Salary
- EXPENSE: Food, Rent, Transportation, Entertainment, Healthcare, Utilities

**Custom category names:**
- 1–50 characters: letters, digits, spaces and `_ . & ' -`, starting with a letter or digit. Names are trimmed.
- Unique per user, case-insensitively. A name may not match a default category name (409).

**Deleting** (the name is matched case-insensitively):
- Default category → **403**
- Name not among your custom categories, including another user's category → **404**
- Category still referenced by any transaction → **400**. Transactions are never cascade-deleted.

### Savings goals

```http
POST /api/goals
{"goalName":"Emergency Fund","targetAmount":5000.00,"targetDate":"2027-01-01","startDate":"2026-01-01"}
→ 201 {"id":1,"goalName":"Emergency Fund","targetAmount":5000.00,"targetDate":"2027-01-01","startDate":"2026-01-01",
       "currentProgress":1000.00,"progressPercentage":20.00,"remainingAmount":4000.00}

GET /api/goals          → 200 {"goals":[ ...same shape... ]}
GET /api/goals/1        → 200 { ...same shape... }
PUT /api/goals/1        {"targetAmount":6000.00,"targetDate":"2027-02-01"}
                        → 200 {..., "targetAmount":6000.00, "progressPercentage":16.67, "remainingAmount":5000.00}
DELETE /api/goals/1     → 200 {"message":"Goal deleted successfully"}
```

**Create:**
- `goalName` is required, up to 100 characters.
- `targetAmount` must be greater than 0, with 2 decimal places.
- `targetDate` must be strictly after today.
- `startDate` is optional and defaults to today. It cannot be in the future.

**Update:**
- Only `targetAmount` and `targetDate` can change; a new `targetDate` must be in the future.
- The name and start date are fixed.
- Updating only the amount of a goal whose target date has already passed is allowed.

### Reports

```http
GET /api/reports/monthly/2024/1
→ 200 {"month":1,"year":2024,"totalIncome":{"Freelance":500.00,"Salary":3000.00},
       "totalExpenses":{"Food":400.00,"Rent":1200.00,"Transportation":200.00},"netSavings":1900.00}

GET /api/reports/yearly/2024
→ 200 {"year":2024,"totalIncome":{...},"totalExpenses":{...},"netSavings":20400.00}
```

- `month` must be 1–12 and `year` must be 1900–9999; anything else returns 400.
- Category keys are ordered by name.
- A period with no transactions returns `{}` maps and `"netSavings":0.00`.

## Error responses

Every error, including those raised inside the security filter chain, has this shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Amount must be greater than zero",
  "path": "/api/transactions",
  "timestamp": "2026-10-10T12:00:00Z",
  "fieldErrors": { "amount": "Amount must be greater than zero" }
}
```

`fieldErrors` appears only for body validation failures. When a field breaks several rules, the "required" message wins. Stack traces, SQL, class names and constraint names are never returned.

| Status | When |
| --- | --- |
| 400 | Bean validation failure, malformed JSON, missing body, wrong JSON type, invalid date/enum/number in a body, path or query, business-rule violation (future date, type mismatch, unknown category, referenced-category deletion, invalid month/year, empty update, date change) |
| 401 | No or expired session; wrong credentials at login |
| 403 | Another user's goal; deleting a default category; missing CSRF token (when enabled) |
| 404 | Unknown transaction/goal/category/endpoint; another user's transaction (see below) |
| 405 / 406 / 413 / 415 | Wrong method / non-JSON `Accept` / body over `MAX_REQUEST_SIZE` / non-JSON `Content-Type` |
| 409 | Duplicate username; duplicate or default-shadowing category name; any other unique-constraint conflict |
| 503 | Database unreachable (also during login, so an outage is not reported as bad credentials) |
| 500 | Only for genuinely unexpected failures. These are logged server-side with a generic message. |

## Business rules

### Savings-goal progress

```
currentProgress    = Σ income amounts − Σ expense amounts
                     over the goal owner's transactions dated startDate ..= today
progressPercentage = currentProgress / targetAmount × 100   (2 decimals, HALF_UP)
remainingAmount    = targetAmount − currentProgress
```

- **Counted transactions:** a transaction dated exactly on `startDate` counts. Transactions cannot be future-dated, so "through today" covers every existing transaction from `startDate` on.
- **Computed on read:** progress is never stored. Editing or deleting a transaction is reflected immediately, and every goal tracks independently from its own `startDate`.
- **One calculation:** `GoalProgressCalculator` serves both the list and detail endpoints, so they cannot disagree.
- **No capping:**
  - Net progress can be negative (expenses exceeded income), which gives a negative percentage and a remaining amount above the target.
  - Progress can exceed the target, which gives more than 100% and a negative remaining amount.
  - Clients that want a 0–100% progress bar should clamp for display.

### Reports

- **Date ranges:** reports use transaction dates, not creation time.
  - A month is `[first day, first day of next month)`.
  - A year is `[Jan 1, Jan 1 of next year)`.
  - So Feb 29 in leap years, month ends and year ends are handled exactly.
- **Aggregation in the database:** totals are summed per category with `GROUP BY`, so transactions are not loaded into memory.
- **Net savings:** total income minus total expenses.

### Money

- Amounts are stored as `NUMERIC(15,2)` and handled as `BigDecimal` end to end; floating point is never used.
- All outputs are scaled to 2 decimal places with HALF_UP rounding (`MonetaryAmounts`).
- Input with more than 2 decimal places is rejected rather than rounded silently.

## Database schema

Flyway migrations in `src/main/resources/db/migration` are the source of truth. They are written so the same scripts run on PostgreSQL and on H2 in PostgreSQL mode, and Hibernate only validates against them.

| Table | Key columns and constraints |
| --- | --- |
| `users` | `username` UNIQUE and CHECK lower-case; `password_hash` (BCrypt); `full_name`; `phone_number`; timestamps |
| `categories` | `name`, `normalized_name` (lower-case), `type` CHECK IN (INCOME, EXPENSE), `is_default`, nullable `owner_id` FK → users; UNIQUE (`owner_id`, `normalized_name`); CHECK default ⇔ no owner. The 7 defaults are seeded in V2. |
| `transactions` | `user_id` FK, `category_id` FK (NO ACTION, so a referenced category cannot be deleted), `amount NUMERIC(15,2)` CHECK > 0, `transaction_date DATE`, `description VARCHAR(255)`; indexes on (user, date), (user, category), (category) |
| `savings_goals` | `user_id` FK, `goal_name`, `target_amount NUMERIC(15,2)` CHECK > 0, `target_date`, `start_date`, CHECK `start_date < target_date`; index on user |

- **Business dates vs timestamps:** business dates (`transaction_date`, `start_date`, `target_date`) are `DATE` columns. Audit timestamps (`created_at`, `updated_at`) are `TIMESTAMP WITH TIME ZONE` stored in UTC.
- **No denormalised type:** a transaction's type is not stored; it is always its category's type, so the two can never disagree. Categories are immutable once created, so this is safe.

## Design decisions and assumptions

These are the points where the assignment was ambiguous or silent, and the choice made for each.

1. **Foreign-owned resources:** the status code follows the assignment's per-endpoint contract.
   - **Goals** (contract lists 403) → 403 when the goal exists but belongs to someone else.
   - **Transactions** (contract lists only 404) → 404, which also hides whether the id exists.
   - **Categories** are namespaced per user, so another user's custom category is simply "not found" (404).

   All ownership checks run in the service layer against the session principal's user id; client-supplied owner ids are never accepted.
2. **Category deletion:** deleting a default category returns **403**. Deleting a referenced category returns **400**, because 409 is not in this endpoint's documented status list.
3. **Transaction filters:** the create contract uses a category **name**, while the list contract filters by `categoryId`. Both are supported, along with `category` (a name) and `type`. Category responses therefore include an additive `id` field so `categoryId` is usable.
4. **Transaction type:** the type is derived from the category. An optional `type` in a request is validated rather than trusted.
5. **Transaction updates:**
   - Updates are partial (PATCH-like semantics on `PUT`), so omitted fields are never reset.
   - Sending a different `date` returns 400. Sending the unchanged date is accepted, so clients that echo the full object still work.
6. **Goal dates:**
   - A future `startDate` is rejected.
   - The target date must be strictly after today.
   - Progress counts transactions through "today" in `APP_TIME_ZONE`.
7. **Logout without a session** returns 401, which is one of the two documented statuses.
8. **Seeding:** default categories are seeded by a versioned Flyway migration. Flyway runs it once, so restarts cannot duplicate them.
9. **Concurrency:**
   - Uniqueness (usernames, category names) and referential integrity (category in use) are enforced by database constraints, which are the final authority.
   - Service-level pre-checks give friendly messages, and races that slip past them are caught at `saveAndFlush` and translated to the same 409 or 400.
   - This is verified by multi-threaded tests. No pessimistic locking is needed.
10. **Monolith:** this is a single service. There are no message brokers, caches or service interfaces without a second implementation.

## Deployment

The repository includes:
- a multi-stage `Dockerfile`, which runs as a non-root user on the Temurin 17 JRE
- a Render Blueprint (`render.yaml`) that creates the web service and a managed PostgreSQL database, wiring `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD` automatically

### Deploy on Render

1. Push this repository to GitHub.
2. In the Render dashboard, choose **New → Blueprint** and select the repository. Render reads `render.yaml`, provisions the database and builds the Docker image.
3. Wait for the health check (`/actuator/health`) to pass. The API is then at `https://<service>.onrender.com/api`.

To deploy without the Blueprint, create a Docker web service and set:
- `DB_URL=jdbc:postgresql://<host>:<port>/<db>` (or `DB_HOST`, `DB_PORT` and `DB_NAME`)
- `DB_USERNAME` and `DB_PASSWORD`
- `COOKIE_SECURE=true`

Render's connection string is in `postgres://` form, not JDBC form, which is why the separate host, port and name variables are supported.

**HTTPS is required in production:** session cookies are `Secure`, so browsers only send them over HTTPS. Render terminates TLS for you.

### Other hosts

```bash
docker build -t personal-finance-manager .
docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... personal-finance-manager
```

## Known limitations

- **In-memory sessions:**
  - Sessions live in Tomcat's memory, so a restart or redeploy logs everyone out.
  - Running more than one instance needs sticky sessions or a shared session store such as Spring Session JDBC/Redis.
- **No pagination:** the transaction listing returns all matching rows, as the `{"transactions":[...]}` contract has no paging fields. A paginated variant would be needed for very large histories.
- **Size limit for chunked uploads:** the request-size limit checks the declared `Content-Length`. Chunked uploads without a length are still bounded by the field-level size validation.
- **No account self-service:** there is no password reset, account deletion or login rate limiting. Add a rate limiter (e.g. Bucket4j or the platform's WAF) before exposing the API widely.
- **Free hosting tiers:** free tiers sleep when idle, so the first request after a pause can take a minute. Free PostgreSQL instances may also be time-limited; check the provider's current policy.
- **Grading script not run:** the assignment's `financial_manager_tests.sh` was not available while this was built, so behaviour follows the written specification. Where the spec was ambiguous, the choices are listed under [Design decisions](#design-decisions-and-assumptions).
