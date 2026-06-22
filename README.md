# Capacity Planner

A strategic capacity planning tool for engineering teams. Model team capacity, decompose initiatives into epics, detect skill shortfalls and over-allocation, and simulate the impact of changes before committing them.

---

## Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 26 | Required to build and run the backend |
| Maven | 3.9+ | Bundled via `./mvnw` — no separate install needed |
| Node.js | 18+ | Required to build and run the frontend |
| PostgreSQL | 14+ | Must be running locally before starting the backend |

### PostgreSQL setup

Create a database and user matching the defaults in `application.yml`:

```sql
CREATE USER capacity_planner WITH PASSWORD 'capacity_planner';
CREATE DATABASE capacity_planner OWNER capacity_planner;
```

The schema and seed data are applied automatically by Flyway on first startup.

---

## Starting the application

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend starts on **http://localhost:8080**.  
All API endpoints are under `/api/v1` and require HTTP Basic auth: `admin` / `admin`.

Alternatively, open the `backend/` folder as a Maven project in IntelliJ IDEA and run the `CapacityPlannerApplication` main class.

### Frontend

```bash
cd frontend
npm install      # first time only
npm run dev
```

The frontend starts on **http://localhost:5173**.

---

## Accessing the application

Open **http://localhost:5173** in your browser. The frontend connects to the backend at `localhost:8080` automatically.

### Main sections

| URL | Description |
|---|---|
| `/` | Dashboard — capacity overview, risk flags |
| `/initiatives` | Initiative list — create, edit, view decomposition |
| `/initiatives/:id` | Initiative detail — epics, gap meter, risk flags |
| `/epics/:id` | Epic detail — skill shortfalls, timeline |
| `/admin/teams` | Teams admin — create/edit teams and capacity factors |
| `/admin/people` | People admin — manage people, skills, and availability overrides |
| `/scenarios` | Scenario Builder — simulate changes before committing |
| `/changelog` | Change log — full audit trail of committed changes |

---

## Additional documentation

All documentation lives in the [`docs/`](docs/) folder:

| File | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Domain model, design decisions, deliberately deferred features |
| [`docs/API.md`](docs/API.md) | REST endpoint reference — request/response shapes, query params |
| [`docs/AGENTS.md`](docs/AGENTS.md) | AI agent guidelines for working in this codebase |
