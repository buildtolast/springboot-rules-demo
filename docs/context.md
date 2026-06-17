# Project Context & Memory

Spring-Kafka-Stream-Rules — living context for humans and agents. 
Source of truth for decisions: [IMPLEMENTATION_CONTEXT_V2.md](../IMPLEMENTATION_CONTEXT_V2.md).
Build plan for the next slice: [HANDOFF.md](../HANDOFF.md).

`Java 21` `Spring Boot 3.3.5` `Gradle 8.10.2` `Kafka Streams` `MongoDB` `as of 2026-06-16`

## 1. The system in one paragraph

Consume JSON events from a **source** Kafka topic. For each event, evaluate a set of **database-stored SpEL boolean rules**. If **any** rule matches (any-match), route the event to a **target** topic. **Every** event — matched or not — produces an audit record to an internal **audit** topic, inside the same exactly-once Kafka transaction as the routing. A **separate** consumer drains the audit topic and writes audit records to **MongoDB** (idempotent upsert), with a dead-letter topic for poison messages.

## 2. Current build state

> [!IMPORTANT]
> **Runs end-to-end in Docker.** `./run.sh` (profile `demo`) starts the stack in the background, produces 25 messages → **13 MATCHED** / 6 UNMATCHED / **6 ERRORED**, 25 audit docs in Mongo. EOS confirmed via transaction commit markers on the target/audit topics.

| Component | Status | Notes |
| :--- | :--- | :--- |
| SpEL eval core (`eval`, `json`, `rules`) | **Done** | TDD; 23 unit + pipeline tests green |
| Kafka Streams topology (`exactly_once_v2`) | **Done** | StreamsBuilder DSL (refactored from low-level Processor API) |
| Audit consumer → MongoDB | **Done** | manual ack after write, `read_committed`, write-concern majority |
| Docker stack (Kafka KRaft + Mongo + app) | **Done** | `docker-compose.yml` |
| Rule store | **Done** | MongoDB truth, Redis cache, CRUD UI |
| Audit DLT retry handler | Open | DLT topic exists; handler not yet wired |

## 3. Locked decisions (D1–D17)

*Do not re-litigate without reason. If you change one, update it in the v2 spec and note the change.*

| # | Decision | Choice |
| :--- | :--- | :--- |
| D1 | Stream framework | Kafka Streams (not plain consumer/producer) |
| D2 | Processing guarantee | `exactly_once_v2` |
| D3 | Audit delivery | Audit produced to internal topic **inside** the EOS txn; separate consumer writes Mongo |
| D4 | Mongo write style | Synchronous `MongoTemplate` upsert, write-concern `majority`, manual ack after Mongo ack |
| D5 | Mongo consistency | Idempotent upsert on deterministic `auditId` → effectively-once. On reprocess with changed rules, last verdict wins |
| D6 | Rule storage | **MongoDB** = truth; **Redis** = cache of active rules; in-JVM `RuleCache` holds compiled `Expression`s; CRUD UI refreshes Redis + Pub/Sub `rules-changed` |
| D7 | Rule language | SpEL, one boolean expression per rule |
| D8 | Rule context root | `Map<String,Object>` from event JSON; indexer syntax `['field']` (NOT a raw `JsonNode`) |
| D9 | Rule combinator | Model 4 — composite single-expression rules; cross-rule = any-match |
| D10 | SpEL security | `SimpleEvaluationContext.forReadOnlyDataBinding()` — rules are untrusted |
| D11 | Audit completeness | MATCHED, UNMATCHED, ERRORED all audited; one record per event |
| D12 | Topology branching | **StreamsBuilder DSL**: `process(RoutingProcessor)` → one keyed `RoutingResult`, split by the DSL (filter→target; mapValues→audit). `@EnableKafkaStreams`, auto-startup off, started after rules load |
| D13 | Language / platform | Java 21, Spring Boot 3.x, spring-kafka 3.x (BOM-managed) |
| D14 | Poison handling | Retry w/ backoff on audit consumer, then route to audit DLT |
| D15 | JSON→Java coercion | Rules assume well-typed JSON; wrong type evaluates false or throws → ERRORED |
| D16 | Source parse failure | Malformed source event must not wedge the stream; becomes an ERRORED audit |
| D17 | Value format | JSON as String/bytes Serde (no Avro/Schema Registry) |

## 4. Package layout

```
com.codrite.ruleaudit
├─ RuleAuditApplication            @SpringBootApplication
├─ config/
│   ├─ AppConfig                   RuleEvaluator, JsonContextFactory, ObjectMapper (JSR-310) beans
│   ├─ KafkaStreamsConfig          @EnableKafkaStreams; StreamsBuilder DSL topology
│   ├─ MongoConfig                 MongoTemplate, write-concern majority
│   ├─ TopicsConfig                NewTopic beans (source/target/audit/DLT)
│   ├─ RuleSeeder                  seeds 22 SpEL rules at startup (order 0)
│   └─ PipelineStarter             reload rules → start streams factory (order 1)
├─ rules/  Rule (JPA) · RuleRepository · RuleCache · RuleLoader · CompiledRule
├─ eval/   RuleEvaluator · EvaluationResult
├─ json/   JsonContextFactory · JsonParseException
├─ topology/ RoutingProcessor · RoutingResult
├─ audit/  AuditRecord · AuditType · AuditKey · AuditConsumer
└─ demo/   DemoMessages · DemoRunner   (profile "demo")
```

## 5. Behavioural semantics

### Verdict (match-wins)

| matched any? | any rule errored? | verdict | routed to target? |
| :--- | :--- | :--- | :--- |
| yes | no | **MATCHED** | yes |
| yes | yes | **MATCHED** | yes |
| no | yes | **ERRORED** | no |
| no | no | UNMATCHED | no |

Rules are independent: a single broken rule never blocks a legitimately matching event. Per-rule errors are always recorded in the audit record for forensics.

### Determinism & idempotency

`auditId = sha256(topic | partition | offset)`. Stable across EOS reprocessing, so the Mongo upsert is idempotent. Audit record is keyed by `auditId` so matched/unmatched records for an event stay co-partitioned.

## 6. Next slice — Complete Docker UI decoupling

Decouple the React UI from the Spring Boot application by moving it into its own dedicated Docker container. This separation allows for independent deployment and scaling of the frontend and backend.

- **New Frontend Container:** Node.js 20 + Nginx to build and serve the React application.
- **Nginx Proxy Configuration:** Handle static file serving and proxy API requests (`/api`) to the Spring Boot backend container.

## 7. How this was built — the local-LLM loop

Codegen is delegated to a local LLM; the orchestrating session specs & reviews. 

- **Bootstrap-then-loop:** scaffolding + `tools/` scripts written once; thereafter codegen runs through `tools/llm-loop.py` (generate → `./gradlew test` → self-fix).
- **TDD:** tests generated single-shot with `tools/llm-gen.sh`, confirmed RED, then impl looped to GREEN. Tests reviewed hardest — they are the contract.
- Specs live in `specs/` (one shared contract + per-file specs) so regeneration is cheap.

## 8. Gotchas carried in the repo

- Gradle launcher pinned to **JDK 21** via `gradle.properties` (host default JDK is too new for Gradle 8.10.2). `.dockerignore` excludes that file so the image build uses the image's JDK 21.
- Single-broker EOS needs replication factors = 1 (`__transaction_state`, `__consumer_offsets`, streams RF) — set in `docker-compose.yml` + `application.yml`.
- Audit consumer must read `isolation.level=read_committed` so it only sees committed EOS transactions.
- SpEL indexer `['field']` works on a `Map` root, NOT a raw `JsonNode`.
- Base starter (no spring-web) does not auto-configure an `ObjectMapper` — one is defined in `AppConfig` with JSR-310.

---
Generated 2026-06-16 · See also [README.md](../README.md) · [IMPLEMENTATION_CONTEXT_V2.md](../IMPLEMENTATION_CONTEXT_V2.md) · [HANDOFF.md](../HANDOFF.md)
