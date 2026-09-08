# 🪵 Splunk Enterprise Local Log Analytics & Observability Guide

This guide provides step-by-step instructions to run **Splunk Enterprise** locally alongside the **ResilientFulfillmentService** microservice stack, configure automated log ingestion, and execute SPL (Splunk Search Processing Language) queries.

---

## 🔑 1. Logging Into Local Splunk Web Dashboard

Once the Docker Compose stack is running (`docker compose up -d`), navigate to:

👉 **[http://localhost:8000](http://localhost:8000)**

* **Username**: `admin`
* **Pa
sword**: `SplunkPassword123`

---

## 📂 2. Configuring Log File Ingestion in Splunk

1. In the Splunk Web UI top navigation bar, navigate to **Settings** $\rightarrow$ **Data Inputs**.
2. Click **Files & Directories** $\rightarrow$ Click **New Local File or Directory**.
3. In the File Path field, enter the container log path:
   ```text
   /var/log/fulfillment-service/resilient-fulfillment.log
   ```
4. Click **Next**.
5. Set **Source Type** to `Automatic` (or `_json`), set **Index** to `main`, and click **Review & Submit**.

---

## 🧪 3. Generating Live Test Log Data

Open the interactive Web Dashboard at **[http://localhost:8080](http://localhost:8080)** and trigger test events:
* 🟢 **Process Normal Order**: Generates `FULFILLED` status events written to PostgreSQL, Redis, and Logback logs.
* 🔴 **Trigger Circuit Breaker**: Generates `FALLBACK_PROCESSING` events caught by Resilience4j.
* 🟡 **Trigger Latency Spike**: Generates slow-call events with 3000ms response delays.

---

## 🔎 4. Useful SPL (Splunk Search Processing Language) Queries

In Splunk, click **Apps** $\rightarrow$ **Search & Reporting**, and enter these queries into the search bar:

### Query A: Search All Microservice Logs
```splunk
index=main "resilient-fulfillment-service"
```

### Query B: Filter for Circuit Breaker & Fallback Events
```splunk
index=main status="FALLBACK_PROCESSING"
```

### Query C: Chart Order Status Distribution
```splunk
index=main | stats count by status
```
*(Switch to the **Visualization** tab to render as a Pie Chart or Bar Graph)*

### Query D: Trace Requests by MDC Trace ID
```splunk
index=main traceId="*"
```

### Query E: Calculate $p95$ and Average Latency
```splunk
index=main | stats avg(latency_ms) as avg_latency p95(latency_ms) as p95_latency by status
```

---

## 🧱 Docker Container Architecture

Splunk Enterprise runs inside Docker on the shared `fulfillment-network` bridge:

| Service Name | Container Name | Host Port | Description |
| :--- | :--- | :--- | :--- |
| `splunk` | `fulfillment-splunk` | `8000`, `8088` | Splunk Web UI & HTTP Event Collector (HEC) |
| `fulfillment-service` | `fulfillment-app` | `8080` | Spring Boot 3 Microservice |
| `postgres` | `fulfillment-postgres` | `5433` | PostgreSQL 15 Database |
| `redis` | `fulfillment-redis` | `6379` | Redis 7 Caching Server |
