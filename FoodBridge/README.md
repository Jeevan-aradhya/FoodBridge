# FoodBridge - Food Donation & Waste Reduction Platform

> **Good Food. Shared Hope. Less Waste.**

FoodBridge is a full-stack Java web application that connects people who have **surplus food** (restaurants, bakeries, supermarkets, event organizers) with **NGOs and shelters** that need it, and **volunteers** who collect and deliver it. Administrators keep the platform safe.

It is built as a portfolio project with Spring Boot 3, Spring Security, Spring Data JPA, MySQL, Thymeleaf and Bootstrap 5.

> **Status of this build (please read):** the source code, tests and documentation were generated in an environment without access to Maven Central, so the project was **not compiled and its tests were not run there**. Only a syntax check of the Java files was done. Run `mvn clean verify` on your machine first and fix anything the compiler reports (see [Troubleshooting](#troubleshooting)). Nothing in this README claims the app is deployed.

---

## 1. The real-world problem

Large amounts of edible food are thrown away while people nearby go hungry. Donors rarely know who can use their surplus before it spoils, and NGOs rarely know what is available. FoodBridge adds a **deadline-aware marketplace** and a **volunteer delivery workflow** between them.

FoodBridge is a *coordination tool*. It does **not** inspect or certify food. Donors and recipients must follow the food-safety rules that apply to them.

## 2. Features

| Area | What you get |
|---|---|
| Roles | DONOR, NGO / RECIPIENT, VOLUNTEER, ADMIN with backend-enforced permissions |
| Donations | Create/edit/remove listings with quantity, unit, prepared/expiry/pickup times, dietary, allergens, storage notes, optional photo |
| Requests | Recipients request a quantity; donors accept or reject; recipients can cancel |
| No double booking | Quantity is reserved on **accept** inside a transaction with pessimistic locks (see section 6) |
| Deliveries | OPEN -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED; street addresses revealed only to people involved |
| Expiry | Live countdowns, "closing soon" highlight, expired listings shown unavailable, background job marks them EXPIRED |
| Smart matching | Transparent rule-based suggestions with the reasons for each (not AI) |
| Impact dashboard | Totals computed from real rows; weekly Chart.js chart; CSV export |
| Notifications | In-app (always) + optional e-mail |
| Search | By name, category, city, minimum quantity, availability, pickup window; sorting and pagination |
| Admin | User list and suspend/reactivate, listing reports, disable/enable listings, requests, assignments, audit log |
| API | REST under `/api/**`, documented with Swagger UI |
| Security | BCrypt, session login, CSRF, role rules, upload checks, security headers, immediate effect of suspension |

## 3. Technology stack

* Java 17 (also runs on 21), Spring Boot 3.3, Maven
* Spring Web, Spring Security, Spring Data JPA (Hibernate), Bean Validation, Actuator
* MySQL 8 (H2 in-memory for the `dev` profile)
* Thymeleaf + `thymeleaf-extras-springsecurity6`, Bootstrap 5.3, Bootstrap Icons, Chart.js, Fetch API
* springdoc-openapi (Swagger UI)
* JUnit 5, Mockito, Spring Boot Test, Spring Security Test
* Docker / Docker Compose

## 4. Architecture

One Spring Boot application serves both the HTML pages and the REST API (no microservices).

```
Browser --(HTML forms / Fetch)--> Controllers --> Services --> Repositories --> MySQL
                                   |  (web + api)   |  (rules,       (Spring Data JPA)
                                   |                |   transactions,
                                   |                |   locking, audit)
                              Spring Security (session, CSRF, roles)
```

* **controller/** - Thymeleaf controllers and `controller/api` REST controllers. They only translate HTTP to service calls.
* **service/** - all business rules. Every method that changes data is `@Transactional`.
* **repository/** - Spring Data interfaces, locking queries and `EntityGraph` fetches.
* **entity/** - JPA tables. **dto/** - what leaves the service layer (entities are never serialized, so no circular JSON).
* **security/** + **config/SecurityConfig** - login, roles, CSRF, account-status filter.
* **exception/** - business exceptions + one handler for the API (JSON) and one for web pages (flash message / error page).

### Main flow in plain language

1. A **donor** lists food with a pickup deadline. It is `AVAILABLE`.
2. An **NGO** requests a quantity. The request is `PENDING`; nothing is reserved yet.
3. The **donor accepts**. In one transaction the app locks the donation row, checks the deadline and remaining quantity, subtracts the quantity, marks the request `ACCEPTED` and creates a delivery task (`OPEN`).
4. A **volunteer** accepts the task (`ASSIGNED`) and updates it to `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`.
5. On `DELIVERED` the request becomes `COMPLETED` and, if everything was delivered, the donation becomes `COMPLETED`.
6. Every important step writes a notification and an audit-log row.

## 5. Database design

```mermaid
erDiagram
    USERS ||--o| ORGANIZATION_PROFILES : "donor or NGO details"
    USERS ||--o| VOLUNTEER_PROFILES : "volunteer details"
    USERS ||--o{ FOOD_DONATIONS : "donor lists"
    USERS ||--o{ DONATION_REQUESTS : "recipient makes"
    FOOD_DONATIONS ||--o{ DONATION_REQUESTS : "receives"
    DONATION_REQUESTS ||--o| PICKUP_ASSIGNMENTS : "accepted request has one task"
    USERS ||--o{ PICKUP_ASSIGNMENTS : "volunteer takes"
    USERS ||--o{ NOTIFICATIONS : "receives"
    AUDIT_LOGS }o..o{ USERS : "actor id (no FK on purpose)"
    USERS { bigint id PK string email UK string password_hash string role string status }
    FOOD_DONATIONS { bigint id PK bigint donor_id FK int quantity int remaining_quantity string status datetime pickup_deadline bigint version }
    DONATION_REQUESTS { bigint id PK bigint donation_id FK bigint recipient_id FK int quantity string status }
    PICKUP_ASSIGNMENTS { bigint id PK bigint request_id FK_UK bigint volunteer_id FK string status }
```

Important points:

* `food_donations.remaining_quantity` is the part **not yet reserved**. It goes down when a request is accepted and up again if an accepted request is cancelled.
* `pickup_assignments.request_id` is unique: one delivery task per accepted request.
* Roles are a single enum column on `users` (one role per account keeps the rules simple and explainable).
* Indexes: `users.role`, `food_donations (status, pickup_deadline)`, `category`, `city`, `donor_id`, request `donation_id/recipient_id/status`, notification `(user_id, is_read)`.
* `audit_logs` has **no foreign key** so history survives even if a user is removed.
* Lists use `Page` + `EntityGraph`/fetch joins to avoid N+1 queries; `spring.jpa.open-in-view=false`.
* Tables are created by Hibernate (`ddl-auto=update`). See `docs/database-schema.md`.

## 6. How double booking is prevented

`RequestService.accept()` runs in a transaction and locks rows with `SELECT ... FOR UPDATE` in a fixed order (**donation -> request -> assignment**), then re-checks status, deadline and remaining quantity *after* the lock. Two simultaneous accepts are therefore processed one after the other. `FoodDonation` also has an `@Version` column as a second safety net. `ConcurrencyIntegrationTest` fires 5 simultaneous accepts of 4 units against 10 available and expects exactly 2 to succeed.

## 7. Project structure

```
FoodBridge/
  pom.xml  Dockerfile  docker-compose.yml  .env.example  .gitignore  README.md
  docs/ database-schema.md  api-guide.md
  src/main/java/com/foodbridge/
     config/ controller/ controller/api/ dto/ entity/ enums/ exception/
     repository/ security/ service/ util/
  src/main/resources/
     application.yml  application-dev.yml  db/init.sql
     static/ css/ js/ images/placeholders/
     templates/ fragments/ auth/ donations/ dashboards/ admin/ pages/ error/
  src/test/java/com/foodbridge/ service/ web/
```

## 8. Prerequisites

* JDK 17 or 21 (`java -version`)
* Maven 3.9+ (`mvn -v`) - or use IntelliJ's bundled Maven
* MySQL 8 (only for the MySQL setup; not needed for the `dev` profile)
* Internet access in the browser (Bootstrap, icons, Chart.js and fonts are loaded from CDNs)

## 9. Quick start (no database needed): dev profile

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Open <http://localhost:8080>. The `dev` profile uses an in-memory H2 database and **loads demo data** (see section 12). Data disappears when you stop the app.

## 10. Run with MySQL

1. Create the database and user (edit the password first):
   ```bash
   mysql -u root -p < src/main/resources/db/init.sql
   ```
2. Set environment variables (see `.env.example`). Example for macOS/Linux:
   ```bash
   export DB_USERNAME=foodbridge
   export DB_PASSWORD='your-password'
   # optional demo data, local machine only:
   export SEED_ENABLED=true
   mvn spring-boot:run
   ```
   Windows PowerShell: `$env:DB_PASSWORD="your-password"; mvn spring-boot:run`
3. Hibernate creates the tables on first start.

### Create your first administrator (without demo data)

```bash
export ADMIN_EMAIL=you@example.com
export ADMIN_PASSWORD='a-long-private-password'   # at least 10 characters
mvn spring-boot:run
```
The account is created only if no admin exists. There is **no** built-in admin and no fixed admin password.

## 11. Run in IntelliJ IDEA

1. *File > Open* the `FoodBridge` folder and let Maven import.
2. Install/enable the **Lombok** plugin and turn on *Settings > Build > Compiler > Annotation Processors > Enable annotation processing*.
3. Open `FoodBridgeApplication`, *Edit Configurations*, set **Active profiles** to `dev` (or set the MySQL environment variables).
4. Run. Open <http://localhost:8080>.

## 12. Demo accounts and demo data

Demo data is created **only** when `foodbridge.seed.enabled=true` (automatic in the `dev` profile, or `SEED_ENABLED=true`) **and** the database has no users. All names/addresses are fictional. Every demo account uses one local-only password: `SEED_DEMO_PASSWORD` (default `Demo@12345`, stored BCrypt-hashed).

| Role | Email |
|---|---|
| Admin | `admin@foodbridge.test` |
| Donors | `donor1@foodbridge.test`, `donor2@...`, `donor3@...` |
| NGOs | `ngo1@foodbridge.test`, `ngo2@...` |
| Volunteers | `volunteer1@foodbridge.test`, `volunteer2@...` |

The seed creates 12 live listings, 1 expired listing, 6 historical delivered donations, and requests/tasks in every state (pending, accepted/open, assigned, in transit, delivered, rejected, cancelled) using the real services.
**Never enable seeding on a public server.**

## 13. API documentation

* Swagger UI: <http://localhost:8080/swagger-ui.html>
* OpenAPI JSON: <http://localhost:8080/v3/api-docs>
* Details and examples: `docs/api-guide.md`

The API uses the same session cookie as the website. Log in at `/login` in the same browser, then try GET endpoints in Swagger. State-changing calls need the CSRF token (header `X-CSRF-TOKEN`, available in the page's `<meta name="_csrf">`), except `POST /api/auth/register`.

## 14. Tests

```bash
mvn clean verify          # compile + all tests
mvn test -Dtest=RequestServiceTest
```

* `RequestServiceTest`, `DonationServiceTest`, `StateAndUtilityTest` - Mockito unit tests: validation, insufficient quantity, expired donations, invalid state transitions, ownership checks, CSV and upload signature checks.
* `SecurityAndApiIntegrationTest` - registration, BCrypt, login, role-based access, CSRF, API responses and error format.
* `ConcurrencyIntegrationTest` - simultaneous accepts cannot over-reserve.
* `DeliveryFlowIntegrationTest` - full request -> delivery lifecycle and invalid delivery transitions.

## 15. Troubleshooting

| Problem | Fix |
|---|---|
| `cannot find symbol ... getName()` / missing getters | Lombok not active. Enable annotation processing in your IDE; Maven builds work without extra steps. |
| `Access denied for user 'foodbridge'` | Wrong `DB_USERNAME`/`DB_PASSWORD`, or `db/init.sql` not run. |
| `Communications link failure` | MySQL not running or wrong `DB_URL`. Try the `dev` profile. |
| Port 8080 in use | `SERVER_PORT=9090 mvn spring-boot:run` |
| Pages look unstyled | Browser cannot reach the CDNs (offline). Connect to the internet. |
| A template error page (500) after a change | Check the console log for `TemplateProcessingException`; the line number is shown. |
| Login says "suspended" | An admin suspended the account; reactivate in Admin > Users. |
| Tests fail on H2 locking | Make sure you run with the `test` profile (the tests set it automatically). |

## 16. Production build

```bash
mvn clean package
java -jar target/foodbridge-1.0.0.jar
```
Production checklist: strong DB password, `COOKIE_SECURE=true` behind HTTPS, `SEED_ENABLED=false`, set `ADMIN_EMAIL`/`ADMIN_PASSWORD` once, persistent `UPLOAD_DIR`, consider Flyway for schema migrations instead of `ddl-auto=update`.

### Docker

```bash
cp .env.example .env     # edit passwords
docker compose up --build
```
Open <http://localhost:8080>. (Set `SEED_ENABLED=true` in `.env` only for a local demo.)

### Hosting (optional)
Any host that runs a Java 17 JAR or a Docker image works (Render, Railway, an AWS Elastic Beanstalk / EC2 instance). Provide the environment variables from `.env.example` and a managed MySQL database. Nothing is deployed by this repository.

## 17. Future enhancements

Flyway migrations, email verification and password reset, rate limiting on login, map integration and distance filtering, image storage on S3, push notifications, volunteer ratings, recurring donations, multi-language UI, Redis for sessions when running several instances.

## 18. Upload to GitHub

```bash
cd FoodBridge
git init
git add .
git commit -m "FoodBridge: initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/foodbridge.git
git push -u origin main
```
`.gitignore` already excludes `.env`, `target/`, `uploads/` and IDE files. Never commit real passwords.

## 19. Resume-ready description

> **FoodBridge - Food Donation & Waste Reduction Platform** | Java 17, Spring Boot 3, Spring Security, Spring Data JPA/Hibernate, MySQL, Thymeleaf, Bootstrap 5, Chart.js, JUnit 5, Mockito, Docker
> * Built a role-based (donor / NGO / volunteer / admin) web application with session authentication, BCrypt hashing, CSRF protection and server-side authorization.
> * Designed a relational schema and REST API (Swagger documented) with DTOs, pagination, specifications-based search and a global exception handler.
> * Implemented double-booking protection using transactions and pessimistic locking, validated with a multi-threaded integration test.
> * Modelled donation, request and delivery state machines with audit logging, in-app notifications, expiry scheduling and CSV/Chart.js reporting.
> * Wrote unit and integration tests with JUnit 5, Mockito and MockMvc; containerised with Docker Compose.
