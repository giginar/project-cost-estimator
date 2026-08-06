# Cost Estimator REST API

Backend dependency boundaries and the behavior-preserving refactor are documented in
[`docs/backend-hexagonal-architecture.md`](docs/backend-hexagonal-architecture.md).

Base path: `/api/v1`

## Authentication and roles

All project and administration endpoints require `Authorization: Bearer <accessToken>`. Obtain an eight-hour opaque access token from `POST /auth/login`.

- `ENGINEER`: full project, schedule, resource, and cost editing.
- `MANAGER`: read-only project/resource access plus `PUT /projects/{projectId}` for Project Settings.
- `ADMIN`: user administration only; project endpoints are denied.

Authentication endpoints:

- `POST /auth/login`
- `POST /auth/register` (creates a pending `MANAGER` account)
- `GET /auth/verify?token=...`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `GET /auth/me`
- `POST /auth/logout`
- `GET|POST /admin/users` (`ADMIN` only)
- `GET /admin/users/mail-outbox` (`ADMIN` only, development email mode)

Passwords are stored as BCrypt hashes. Registration and admin-created accounts cannot sign in until the 24-hour email-verification link is used. Configure SMTP with `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `MAIL_FROM`, and set `MAIL_DELIVERY_ENABLED=true`. The SMTP health check follows `MAIL_DELIVERY_ENABLED`; it can be overridden with `MAIL_HEALTH_ENABLED`. With delivery disabled, no SMTP connection is attempted and verification messages are logged and exposed only to administrators in the development mail outbox.

Password-reset requests always return the same response whether an account exists or not. Reset links contain a cryptographically random token, expire after 30 minutes, can be used only once, and invalidate every active session for that user after the password changes.

Demo accounts are already verified:

| Role | Email | Password |
|---|---|---|
| Engineer | `engineer@example.com` | `Engineer123!` |
| Manager | `manager@example.com` | `Manager123!` |
| Admin | `admin@example.com` | `Admin123!` |

## Interactive documentation

Run the application with `.\mvnw.cmd spring-boot:run`, then open:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

Swagger UI's **Try it out** button can execute every endpoint directly from the browser.

## Resource catalog

- `GET /resources?type=equipment|personnel|material`
- `GET /resources?projectId={projectId}` (project-owned plus shared resources)
- `GET /resources/{id}`
- `POST /resources/personnel`
- `POST /resources/equipment`
- `POST /resources/materials`
- `POST /resources/{id}/cost-components`
- `PUT|DELETE /resources/{id}/cost-components/{costId}`
- `PUT /resources/{equipmentId}/equipment-economics`
- `PUT /resources/{materialId}/material-procurement`
- `PUT /resources/{id}/sharing`
- `POST /resources/{equipmentId}/fuel-consumptions`
- `DELETE /resources/{id}?projectId={ownerProjectId}`

## Project planning and estimation

- `GET|POST /projects`
- `GET|PUT|DELETE /projects/{projectId}`
- `POST /projects/{projectId}/estimates`
- `POST /projects/{projectId}/estimates/{estimateId}/wbs-items`
- `POST /projects/{projectId}/estimates/{estimateId}/wbs-items/{wbsId}/activities`
- `PUT /projects/{projectId}/estimates/{estimateId}/activities/{activityId}`
- `PUT /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/planning`
- `POST /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies`
- `DELETE /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/dependencies/{dependencyId}`
- `POST /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments`
- `PUT /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}`
- `DELETE /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}`
- `POST /projects/{projectId}/estimates/{estimateId}/equipment-assignments/{assignmentId}/crew`
- `POST /projects/{projectId}/estimates/{estimateId}/staff`
- `POST /projects/{projectId}/estimates/{estimateId}/resource-rates/{resourceId}/sync`
- `GET /projects/{projectId}/estimates/{estimateId}/resource-rates`
- `PUT /projects/{projectId}/estimates/{estimateId}/resource-rates/{sourceCostComponentId}`
- `GET /projects/{projectId}/estimates/{estimateId}/cost`
- `GET /projects/{projectId}/estimates/{estimateId}/cost-report`
- `GET|POST /projects/{projectId}/estimates/{estimateId}/boq-items`
- `PUT|DELETE /projects/{projectId}/estimates/{estimateId}/boq-items/{boqId}`
- `GET /projects/{projectId}/estimates/{estimateId}/boq-traceability`
- `GET|PUT /projects/{projectId}/calendar`
- `GET|POST /projects/{projectId}/estimates/{estimateId}/pricing-rules`
- `PUT|DELETE /projects/{projectId}/estimates/{estimateId}/pricing-rules/{ruleId}`
- `GET /projects/{projectId}/estimates/{estimateId}/pricing-summary`

The client project picker lists every project and switches the active schedule. Engineers create a project with a baseline estimate first, then add WBS branches from the Schedule page. Activity creation stays disabled until the project has at least one WBS.

The resource referenced by an assignment determines whether it becomes an equipment, personnel, or material assignment. Errors use the standard `application/problem+json` format.

`cost-report` is the authoritative detailed calculation used by the client report and project overview. It returns the estimate total, project-level costs, and matching WBS/activity breakdowns. Every level exposes personnel, equipment, fuel, material, accommodation, transportation, overhead, tax, and total amounts.

BOQ items keep their own unit price and currency and link to a WBS plus an optional activity. Linking synchronizes the activity quantity/unit. Activity planning calculates duration as `ceil(quantity / dailyProductionRate)` and places working dates on the project calendar. Dependencies support finish-to-start, start-to-start, finish-to-finish, start-to-finish and working-day lag with cycle prevention. Shift paid hours determine effective working hours per day. The Angular **BOQ & planning** page manages all of these records and shows BOQ → WBS → activity traceability.

Project currency supports `USD`, `EUR`, and `TRY`. Catalog cost components carry their own `currencyCode` and act as reusable default prices. When a resource is assigned, its rates are copied into the estimate and converted to the project currency using the project's user-defined `usdTryRate` and `eurTryRate`. Project currency changes convert only these estimate snapshots and project additional costs; the shared resource catalog and other projects are not mutated. The rate-sync endpoint adds missing catalog components, while the rate override endpoint changes a price only for the selected estimate. Rates are never fetched or assumed externally.

Catalog cost components can be edited with tax and validity dates. Existing estimate snapshots remain stable until `resource-rates/{resourceId}/sync?replaceExisting=true` is explicitly requested. Equipment economics can generate monthly depreciation, maintenance and insurance catalog components for owned equipment. Material procurement stores supplier, lead time, minimum order quantity and default waste.

Resource creation accepts `projectId` and `shared` query parameters. A project-specific resource is visible only to its owning project; a shared resource is available to every project. Only the owner project can change the sharing flag. New assignments and rate synchronization reject private resources owned by another project, while existing assignments remain intact when sharing is later disabled.

Only the owner project can delete a project-created resource. Deletion is rejected while the resource is referenced by an activity assignment, equipment crew, or project staff record; those assignments must be removed first. Protected system-wide seed resources cannot be deleted.

Pricing rules are ordered percentage calculations over either authoritative estimated cost or the running total. The pricing summary returns BOQ value, on-cost/risk adders, sales price, gross/net profit, profit margin and BOQ variance. The Angular **Pricing & profit** page edits these rules; Overview consumes the same backend summary.

Data is currently kept in memory. Repository classes are outbound adapters behind `ProjectRepositoryPort`,
`ResourceRepositoryPort`, and `UserRepositoryPort`; a JPA adapter can replace them without changing application services or controllers.

## Angular client

The Angular application lives in `frontend`. Start the Spring Boot API on port `8080`, then run:

```shell
cd frontend
npm install
npm start
```

Open `http://localhost:4200`. The development proxy forwards `/api` calls to Spring Boot. When the API contains a project with dated activities, the Gantt chart loads them automatically. Otherwise it displays sample scheduling data. Drag either edge of an activity bar to change its planned date range.
