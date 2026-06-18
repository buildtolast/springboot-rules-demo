# springboot-rules-demo

A Kafka Streams pipeline that evaluates database-stored SpEL rules against JSON events, routes matches, and audits *every* event to MongoDB — exactly-once.

`Java 21` `Spring Boot 3.3.5` `React 18` `Tailwind CSS 4` `Kafka Streams (EOS v2)` `MongoDB` `Redis` `Docker Compose`

## What it does

Consume JSON events from a **source** topic. For each event, evaluate a set of **SpEL boolean rules**. If **any** rule matches (any-match), a specialized **match-confirmation event** is generated for **each** matching rule and routed to a **target** topic. This new event includes a confirmation message, a combined type (`originalType_ruleId`), and the original payload. For **every rule** evaluated against an event, an **audit record** (linking to its generated event if matched) is produced to an internal **audit** topic *inside the same exactly-once transaction*. This provides a granular audit trail showing exactly why each rule matched or failed. A separate consumer drains the audit topic and writes records to **MongoDB** with an idempotent upsert (effectively-once).

> [!NOTE]
> Kafka EOS does not span the Mongo write (no XA is possible across Kafka + Mongo). The audit topic + deterministic `auditId` + idempotent upsert give **effectively-once** persistence instead.

## Architecture

The system is fully containerized and decoupled, separating the high-performance streaming engine from the administrative Rules Engine Dashboard.

### Processing Model: Sync vs Async

| Layer | Nature | Reason |
| :--- | :--- | :--- |
| **Stream Evaluation** | **Synchronous** | Guarantees sub-millisecond latency and Exactly-Once Semantics (EOS). Every rule is evaluated in-memory before the transaction commits. |
| **Audit Persistence** | **Asynchronous** | Decouples the database (MongoDB) from the streaming hot-path. Prevents DB pressure from causing Kafka consumer lag. |
| **Rule Updates** | **Reactive** | Uses Redis Pub/Sub to instantly invalidate caches across multiple instances without restarting the streams. |
| **Analytics** | **Reactive** | Uses non-blocking MongoDB aggregations to provide real-time dashboards without impacting API responsiveness. |

### Performance: Millisecond Processing

The system achieves sub-millisecond evaluation times by:
1. **Pre-compiled Rules:** SpEL expressions are compiled into executable objects upon loading, not per-message.
2. **L1 Cache (RuleCache):** Rules are held in a local `ConcurrentHashMap` for zero-latency lookup during stream processing.
3. **Optimized JSON Access:** Uses `StandardEvaluationContext` with security-hardened type-locating for high-performance, read-only data binding.
4. **Bulk Fan-out:** Every evaluation results in a dedicated audit record, batched within the Kafka Producer transaction.

### Application Flow

The following diagram illustrates the lifecycle of an event as it moves through the system, highlighting the transactional boundaries and the asynchronous audit persistence.

#### Visual Flow

```text
  [ Input ]          [ Kafka Streams Engine (Sync/EOS) ]          [ Persistence ]
      │                         (Transaction)                          (Async)
      │                               │                                   │
┌─────▼─────┐           ┌─────────────▼─────────────┐             ┌───────▼───────┐
│  Source   │           │ 1. Read JSON Event        │             │ 4. Reactive   │
│  Events   ├──────────▶│ 2. Evaluate SpEL Rules    │             │    Subscribe  │
└───────────┘           │ 3. Generate New Events    │             └───────┬───────┘
                        └──────┬──────────────┬─────┘                     │
                               │              │                   ┌───────▼───────┐
                        IF MATCHED      ALWAYS (Audit)            │ 5. Idempotent │
                               │              │                   │    Upsert     │
                        ┌──────▼──────┐┌──────▼──────┐            └───────┬───────┘
                        │  New Events ││    Audit    │◀── (Topic) ────────┘
                        │   (Target)  ││    Events   │            ┌───────▼───────┐
                        └─────────────┘└─────────────┘            │ 6. Kafka Ack  │
                                                                  └───────────────┘
```

#### Detailed Lifecycle (Mermaid)
flowchart LR
    subgraph Input["Input Stage"]
        Source[("source-events")]
    end

    subgraph Streaming["Kafka Streams Engine (Sync/Transactional)"]
        direction TB
        Read["1. Read Event"]
        Eval["2. Evaluate Rules (in-memory)"]
        Route{"3. Match Found?"}
        Target[("target-events")]
        AuditTopic[("audit-events")]

        Read --> Eval
        Eval --> Route
        Route -- "Yes" --> Target
        Route -- "Always" --> AuditTopic
    end

    subgraph Persistence["Audit Consumer (Async/Reactive)"]
        direction TB
        Consume["4. Reactive Subscribe"]
        Upsert["5. Idempotent Upsert"]
        Mongo[("MongoDB (audits)")]
        Ack["6. Kafka Acknowledge"]

        AuditTopic -.-> Consume
        Consume --> Upsert
        Upsert --> Mongo
        Mongo --> Ack
    end

    Source --> Read

    style Streaming fill:#f9f9f9,stroke:#333,stroke-dasharray: 5 5
    style Persistence fill:#f0f4ff,stroke:#0052cc
```

### Flow Breakdown

1.  **Ingestion:** The `RoutingProcessor` (Kafka Streams) consumes a JSON event from the `source-events` topic.
2.  **Synchronous Evaluation:**
    *   The event is evaluated against all active rules stored in the local `RuleCache`.
    *   Evaluation happens in-memory using pre-compiled SpEL expressions for sub-millisecond latency.
3.  **Transactional Routing & Fan-out:**
    *   If **any** rule matches, a specialized **match-confirmation event** is generated for **each** matching rule and sent to the `target-events` topic.
    *   The generated event contains:
        *   `message`: Confirmation of match.
        *   `type`: Combined string `originalType_ruleId` (e.g., `transaction_amount-over-1000`).
        *   `original_event`: The full original payload nested inside.
    *   For **every** rule evaluated, a granular `AuditRecord` (containing result, reason, and the generated event if matched) is sent to the `audit-events` topic.
    *   *Crucial:* Both the target route and the audit fan-out happen within the **same Kafka Transaction**. If one fails, nothing is committed.
4.  **Reactive Persistence:**
    *   The `AuditConsumer` subscribes to the `audit-events` topic.
    *   It uses **Spring Data MongoDB Reactive** to perform non-blocking, idempotent upserts into the `audits` collection.
    *   The Kafka offset is only acknowledged *after* the MongoDB write is confirmed, ensuring "effectively-once" delivery.

### Visual Architecture

```text
    ┌─────────────────────┐
    │    User Browser     │
    └──────────┬──────────┘
               │ Port 8080
               ▼
    ┌─────────────────────┐         ┌────────────────────────┐
    │   Nginx Container   │         │  Docker Compose Stack  │
    │ (React Dashboard)   │         └──────────┬─────────────┘
    └──────────┬──────────┘                    │
               │ Proxy /api                    │
               ▼                               │
    ┌─────────────────────┐                    │
    │ Backend (Spring)    │                    │
    │ REST API / Streams  │◀───────────────────┘
    └──────────┬──────────┘
               │
      ┌────────┴────────┬─────────────────┐
      │                 │                 │
      ▼                 ▼                 ▼
┌──────────┐      ┌──────────┐      ┌──────────┐
│ MongoDB  │◀────▶│  Kafka   │◀────▶│  Redis   │
│ (Audits) │      │ (Events) │      │ (Cache)  │
└──────────┘      └──────────┘      └──────────┘
```

### Detailed Flow (Mermaid)

```mermaid
flowchart TD
    subgraph External["External Access"]
        Browser[User Browser]
    end

    subgraph Cluster["Docker Compose Network"]
        subgraph Frontend["Frontend Container (Nginx)"]
            UI[React Pro Dashboard]
        end

        subgraph Backend["Backend Container (Spring Boot)"]
            API[REST API]
            Engine[Kafka Streams Engine]
            Consumer[Audit Consumer]
        end

        Broker[(Kafka Broker)]
        DB[(MongoDB)]
        Cache[(Redis Cache)]
    end

    Browser -- "Port 8080" --> UI
    UI -- "Proxy /api" --> API
    
    API -- "CRUD Rules" --> DB
    API -- "Sync Cache" --> Cache
    API -- "Pub/Sub Notify" --> Cache
    
    Broker -- "source-events" --> Engine
    Engine -- "evaluates SpEL" --> Engine
    Engine -- "matched" --> Broker
    Engine -- "audit logs" --> Broker
    
    Broker -- "audit-events" --> Consumer
    Consumer -- "idempotent save" --> DB
```

## Features

### 🚀 Pro Dashboard (UI)
- **Rule Management:** Create, edit, and delete SpEL rules with real-time status badges.
- **Analytics View:** Tabbed dashboard showing real-time message throughput, evaluation counts, and per-rule performance (Matched vs. Unmatched vs. Errored).
- **Traffic Simulator:** Integrated tool to push synthetic traffic and monitor sub-millisecond processing latency for JSON parsing and rule evaluation.
- **Compliance Reports:** Dedicated tab for business users to view and export matched/unmatched records as CSV files with full metadata.
- **Advanced Search:** Live filtering by rule description or SpEL expression.
- **Modern Stack:** Built with React 18, Vite, Tailwind CSS 4, and Lucide icons.
- **Decoupled Deployment:** Served via Nginx in a separate container for independent scaling.

### 🧠 Intelligent Streaming (Backend)
- **Dynamic Rules:** Rules are stored in MongoDB and cached in Redis for sub-millisecond evaluation.
- **Async Persistence:** Evaluation results are saved to MongoDB using an asynchronous, reactive repository for high-throughput auditing without blocking.
- **Hot Swapping:** Uses Redis Pub/Sub to signal all cluster nodes to reload rules without downtime.
- **Exactly-Once:** Leverages Kafka `exactly_once_v2` for the entire evaluation and auditing pipeline.
- **Observability:** Comprehensive logging across all components using SLF4J and Lombok `@Slf4j`.

## Prerequisites

- **Docker** (Desktop / OrbStack) — for the end-to-end run.
- **JDK 21** — only for local (non-Docker) builds.

## Quick start — run the full pipeline

The `run.sh` script automates the entire stack. It autodetects code changes, rebuilds the necessary Docker images, and starts everything in the background.

### Smart Service Detection

The `run.sh` script automatically detects if Kafka, MongoDB, or Redis are already running on your host machine (using default ports). 
- If detected, the application connects to your **host services** instead of starting Docker containers for them.
- If not detected, it spins up the required services in **Docker containers**.

This allows for a seamless transition between a fully self-contained Docker environment and one where you might be running some infrastructure locally.

```bash
chmod +x run.sh
./run.sh
```

### Advanced Usage

| Action | Command |
| :--- | :--- |
| **View Logs** | `./run.sh --logs` (or `docker compose logs -f`) |
| **3-Node Cluster** | `./run.sh --cluster` |
| **Change UI Port** | `UI_PORT=9090 ./run.sh` |
| **Change API Port** | `APP_PORT=8082 ./run.sh` |
| **Change Kafka Port** | `KAFKA_PORT=9094 ./run.sh` |
| **Force Rebuild** | `./run.sh --build` |
| **Use External Kafka** | `KAFKA_BOOTSTRAP=your-broker:9092 ./run.sh` |
| **Use External Mongo** | `MONGODB_URI=mongodb://your-mongo:27017/db ./run.sh` |
| **Disable Demo Mode** | `SPRING_PROFILES_ACTIVE=prod ./run.sh` |

Watch the `springboot-rules-demo-app` logs for the `DEMO RESULTS` block. A clean run over 25 messages:

| MATCHED | UNMATCHED | ERRORED |
| :--- | :--- | :--- |
| **13** | 6 | 6 |

### Inspect the results

```bash
# audit counts by type
docker exec springboot-rules-demo-mongo mongosh ruleaudit --quiet \
  --eval 'JSON.stringify(db.audits.aggregate([{$group:{_id:"$auditType",n:{$sum:1}}}]).toArray())'

# topic offsets
docker exec springboot-rules-demo-kafka /opt/kafka/bin/kafka-get-offsets.sh \
  --bootstrap-server localhost:9092 --topic audit-events

# tear down
docker compose down -v
```

## Build & Development

While Docker is the recommended way to run the entire stack, you can build and test individual components locally.

### Backend (Spring Boot)
```bash
./gradlew build        # Full build and tests
./gradlew bootJar      # Create executable JAR
```

### Frontend (React)
```bash
cd frontend
npm install
npm run dev            # Start Vite dev server with HMR
```

*Note: The frontend dev server is configured to proxy API requests to `localhost:8081` by default.*

## Configuration

| Env var | Default | Purpose |
| :--- | :--- | :--- |
| `UI_PORT` | `8080` | Host port for the React Dashboard |
| `APP_PORT` | `8081` | Host port for the Backend API. Auto-proxied if non-default and port is open on host. |
| `BACKEND_URL` | | Explicit URL for the UI to proxy to (e.g., `http://localhost:8081`). |
| `CONTAINER_UI_PORT` | `80` | Internal container port for UI |
| `CONTAINER_APP_PORT` | `8080` | Internal container port for API |
| `KAFKA_PORT` | `9092` | Host port for Kafka |
| `MONGO_PORT` | `27017` | Port for MongoDB |
| `REDIS_PORT` | `6379` | Port for Redis |
| `KAFKA_BOOTSTRAP` | | External Kafka broker (e.g., `localhost:9092`) |
| `MONGODB_URI` | | External MongoDB URI (e.g., `mongodb://localhost:27017/db`) |
| `REDIS_HOST` | | External Redis host (e.g., `localhost`) |
| `SPRING_PROFILES_ACTIVE` | `demo` | Set to `prod` to disable the auto-producer |

## Writing rules

A rule is one **SpEL boolean expression**. The evaluation root is a `Map<String,Object>` built from the event JSON, so fields use indexer syntax:

```spel
['amount'] > 1000
['region'] == 'EU' and ['flagged'] == true
['order']['total'] >= 50            // nested
```

> [!WARNING]
> **Security:** rules are untrusted input and are evaluated with `StandardEvaluationContext` with type referencing disabled. Previously used `SimpleEvaluationContext`, but migrated to `StandardEvaluationContext` to support advanced features like selection/projection while maintaining RCE protection by blocking `T()` calls.

## Project layout

```
ruleaudit/
├─ frontend/    React + Vite Pro Dashboard (Nginx)
├─ src/main/    Spring Boot Backend
│  ├─ audit/    Audit Consumer & Record definitions
│  ├─ config/   Infrastucture & Pipeline configuration
│  ├─ demo/     Demo data producer & reporting
│  ├─ eval/     SpEL Evaluation engine
│  ├─ json/     JSON parsing & context mapping
│  ├─ rules/    Persistence, Redis Cache & Change Listeners
│  └─ topology/ Kafka Streams RoutingProcessor
└─ run.sh       End-to-end orchestration script
```

## Status & roadmap

| Area | State |
| :--- | :--- |
| Core Pipeline | **Done**, EOS topology, audit → Mongo |
| Rule Management | **Done**, CRUD API + Redis Sync |
| Pro Dashboard | **Done**, React + Tailwind 4, Analytics & Simulator |
| Observability | **Done**, Slf4j/Lombok & Latency Tracking |
| Error Handling | **Basic**, DLT retry handler pending |

---
Authoritative spec: [IMPLEMENTATION_CONTEXT_V2.md](IMPLEMENTATION_CONTEXT_V2.md) ·
Architecture & decisions: [docs/context.md](docs/context.md)
