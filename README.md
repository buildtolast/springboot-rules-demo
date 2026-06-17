# Spring-Kafka-Stream-Rules

A Kafka Streams pipeline that evaluates database-stored SpEL rules against JSON events, routes matches, and audits *every* event to MongoDB — exactly-once.

`Java 21` `Spring Boot 3.3.5` `Kafka Streams (EOS v2)` `MongoDB` `SpEL rules` `Docker Compose`

## What it does

Consume JSON events from a **source** topic. For each event, evaluate a set of **SpEL boolean rules**. If **any** rule matches (any-match), the event is routed to a **target** topic. **Every** event — matched, unmatched, or errored — produces an **audit record** to an internal **audit** topic *inside the same exactly-once transaction*. A separate consumer drains the audit topic and writes records to **MongoDB** with an idempotent upsert (effectively-once).

> [!NOTE]
> Kafka EOS does not span the Mongo write (no XA is possible across Kafka + Mongo). The audit topic + deterministic `auditId` + idempotent upsert give **effectively-once** persistence instead.

## Architecture

```
producer ─▶ source-events
                 │
        ┌────────▼─────────────────────────────────┐
        │  Kafka Streams app (exactly_once_v2)      │
        │  StreamsBuilder DSL:                       │
        │   source.process(RoutingProcessor)         │
        │      → RoutingResult{matched,routed,audit} │
        │        ├─ filter(matched) → target-events  │
        │        └─ (always)        → audit-events   │
        │  rules: RuleCache (compiled SpEL, in-JVM)  │
        └────────┬───────────────────┬──────────────┘
            target-events        audit-events
                                      │  (read_committed)
                              ┌───────▼────────┐
                              │ AuditConsumer  │ manual ack after write
                              └───────┬────────┘
                                      ▼
                                 MongoDB (audits)  upsert by _id
```

## Prerequisites

- **Docker** (Desktop / OrbStack) — for the end-to-end run.
- **JDK 21** — only for local (non-Docker) builds. The Gradle launcher is pinned to JDK 21 in `gradle.properties` (the repo was authored on a host whose default JDK was too new for Gradle 8.10.2).

## Quick start — run the full pipeline

The `demo` profile produces a batch of messages, lets the pipeline settle, then prints a result summary.

```bash
docker compose up --build
```

Watch the `ruleaudit-app` logs for the `DEMO RESULTS` block. A clean run over 25 messages:

| MATCHED | UNMATCHED | ERRORED |
| :--- | :--- | :--- |
| **13** | 6 | 6 |

25 audit documents land in Mongo. EOS is verifiable from topic end-offsets: `target-events:14` and `audit-events:26` — record counts plus one transaction commit marker each (the source topic, non-transactional, has none).

### Inspect the results

```bash
# audit counts by type
docker exec ruleaudit-mongo mongosh ruleaudit --quiet \
  --eval 'JSON.stringify(db.audits.aggregate([{$group:{_id:"$auditType",n:{$sum:1}}}]).toArray())'

# topic offsets
docker exec ruleaudit-kafka /opt/kafka/bin/kafka-get-offsets.sh \
  --bootstrap-server localhost:9092 --topic audit-events

# tear down
docker compose down -v
```

## Build & test locally (no Docker)

```bash
./gradlew test        # unit + pipeline integration tests
./gradlew bootJar     # build the runnable fat jar
```

Running the app outside Docker requires a reachable Kafka broker and MongoDB; set `KAFKA_BOOTSTRAP` and `MONGODB_URI` accordingly.

## Configuration

| Env var | Default | Purpose |
| :--- | :--- | :--- |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap servers |
| `MONGODB_URI` | `mongodb://localhost:27017/ruleaudit` | Mongo connection |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `demo` to run the producer/report driver |

Topics (`source-events`, `target-events`, `audit-events`, `audit-events.DLT`) are created on startup via `TopicsConfig`.

## Writing rules

A rule is one **SpEL boolean expression**. The evaluation root is a `Map<String,Object>` built from the event JSON, so fields use indexer syntax:

```spel
['amount'] > 1000
['region'] == 'EU' and ['flagged'] == true
['order']['total'] >= 50            // nested
```

> [!WARNING]
> **Security:** rules are untrusted input and are evaluated with `SimpleEvaluationContext.forReadOnlyDataBinding()`. Type references such as `T(java.lang.Runtime)…` cannot execute — they fail safely and are recorded as a rule error.

**Verdict semantics (match-wins):** any rule matching ⇒ `MATCHED` (routed), even if other rules error. `ERRORED` only when nothing matched and at least one rule threw; otherwise `UNMATCHED`. Errors are always recorded for forensics.

## Project layout

```
com.codrite.ruleaudit
├─ RuleAuditApplication
├─ config/   AppConfig, KafkaStreamsConfig (DSL), MongoConfig,
│            TopicsConfig, RuleSeeder, PipelineStarter
├─ rules/    Rule, RuleRepository, RuleCache, RuleLoader, CompiledRule
├─ eval/     RuleEvaluator, EvaluationResult
├─ json/     JsonContextFactory, JsonParseException
├─ topology/ RoutingProcessor, RoutingResult
├─ audit/    AuditRecord, AuditType, AuditKey, AuditConsumer
└─ demo/     DemoMessages, DemoRunner
```

## Status & roadmap

| Area | State |
| :--- | :--- |
| SpEL eval core, EOS topology, audit → Mongo | **Done**, runs E2E in Docker |
| Rule store | MongoDB (synced with Redis) |
| CRUD UI | **Done**, React "Pro Dashboard" (served on port 8080) |
| Audit DLT retry handler, TopologyTestDriver tests | Open |

---
Authoritative spec: [IMPLEMENTATION_CONTEXT_V2.md](IMPLEMENTATION_CONTEXT_V2.md) ·
Architecture & decisions: [docs/context.md](docs/context.md) ·
Generated 2026-06-16.
