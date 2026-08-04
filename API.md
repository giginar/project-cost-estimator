# Cost Estimator REST API

Base path: `/api/v1`

## Interactive documentation

Run the application with `.\mvnw.cmd spring-boot:run`, then open:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

Swagger UI's **Try it out** button can execute every endpoint directly from the browser.

## Resource catalog

- `GET /resources?type=equipment|personnel|material`
- `GET /resources/{id}`
- `POST /resources/personnel`
- `POST /resources/equipment`
- `POST /resources/materials`
- `POST /resources/{id}/cost-components`
- `POST /resources/{equipmentId}/fuel-consumptions`
- `DELETE /resources/{id}`

## Project planning and estimation

- `GET|POST /projects`
- `GET|PUT|DELETE /projects/{projectId}`
- `POST /projects/{projectId}/estimates`
- `POST /projects/{projectId}/estimates/{estimateId}/wbs-items`
- `POST /projects/{projectId}/estimates/{estimateId}/wbs-items/{wbsId}/activities`
- `PUT /projects/{projectId}/estimates/{estimateId}/activities/{activityId}`
- `POST /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments`
- `DELETE /projects/{projectId}/estimates/{estimateId}/activities/{activityId}/assignments/{assignmentId}`
- `POST /projects/{projectId}/estimates/{estimateId}/equipment-assignments/{assignmentId}/crew`
- `POST /projects/{projectId}/estimates/{estimateId}/staff`
- `GET /projects/{projectId}/estimates/{estimateId}/cost`

The resource referenced by an assignment determines whether it becomes an equipment, personnel, or material assignment. Errors use the standard `application/problem+json` format.

Project currency supports `USD`, `EUR`, and `TRY`. `PUT /projects/{projectId}` accepts two user-defined exchange rates: `usdTryRate` means `1 USD = x TRY`, and `eurTryRate` means `1 EUR = x TRY`. The rates are stored with the project and returned by subsequent GET requests, so they only need to be entered once. When `currencyCode` changes, the saved rates are used if the request does not repeat them. The application derives USD/EUR automatically and converts all resource catalog prices and project additional-cost unit prices before saving the new project currency. Rates are never fetched or assumed externally.

Data is currently kept in memory. The repository package is deliberately isolated so it can be replaced by JPA persistence without changing controllers.

## Angular client

The Angular application lives in `frontend`. Start the Spring Boot API on port `8080`, then run:

```shell
cd frontend
npm install
npm start
```

Open `http://localhost:4200`. The development proxy forwards `/api` calls to Spring Boot. When the API contains a project with dated activities, the Gantt chart loads them automatically. Otherwise it displays sample scheduling data. Drag either edge of an activity bar to change its planned date range.
