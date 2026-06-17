# IMPLEMENTATION CONTEXT v2 — Kafka Streams SpEL Rule-Matching & Async Mongo Audit

> **Purpose of this file.** This is the working context/memory for building the
> project. It captures every decision already made, what is still open, the
> package/class layout to create, and the order to build in. Hand this to a
> developer or coding agent and they should be able to start without re-deriving
> anything.
>
> **v2 changelog (what changed from v1 and why).** v1 left several landmines that
> would surface during implementation. v2 resolves them:
> - **D8 rewritten:** SpEL root is now a **`Map<String,Object>`** (converted from
>   JSON via Jackson), *not* a raw `JsonNode`. SpEL's `['field']` indexer resolves
>   natively against `Map`; on `JsonNode` it does **not** without a custom
>   accessor. This was the biggest correctness gap.
> - **New D15 (type coercion):** documented JSON→Java typing rules for comparisons.
> - **New D16 (source deserialization handling):** malformed source events no
>   longer wedge the stream; handled via a `DeserializationExceptionHandler` +
>   ERRORED audit. v1 only protected per-rule eval, not the JSON parse step.
> - **D4 rewritten:** audit consumer uses **synchronous `MongoTemplate` + Java 21
>   virtual threads + manual ack**, not `ReactiveMongoTemplate` inside a blocking
>   listener (which forced a `.block()` and bought nothing).
> - **D12/topology rewritten:** routed event + audit are built in a **single
>   `process()` node** that forwards keyed records to named sinks — no `selectKey`
>   repartition, and `EvaluationResult` never goes on the wire.
> - **D5 clarified:** reprocess-with-changed-rules semantics (last verdict wins).
> - **O5 promoted to locked D17:** value format is JSON String/bytes Serde.
> - **New dev profile note:** RF=1 locally so step 1 doesn't fail on one broker.
> - **AuditRecord gains `schemaVersion`** and an explicit JSON Serde.

---

## 0. One-paragraph statement of the system

Consume JSON events from a **source** Kafka topic. For each event, evaluate a set
of **database-stored SpEL boolean rules** against the event. If **any** rule
matches (any-match), route the event to a **target** topic. **Every** event —
matched or not — produces an audit record to an internal **audit** topic, inside
the same exactly-once Kafka transaction as the routing. A **separate** consumer
drains the audit topic and writes audit records to **MongoDB** (idempotent
upsert), with a dead-letter topic for poison messages.

---

## 1. Locked decisions (do not re-litigate without reason)

| # | Decision | Choice |
|---|---|---|
| D1 | Stream framework | Kafka Streams (not plain consumer/producer) |
| D2 | Processing guarantee | `exactly_once_v2` |
| D3 | Audit delivery | Audit payload produced to internal audit topic **inside** the EOS txn; separate consumer writes to Mongo |
| D4 | Mongo write style | **Synchronous `MongoTemplate`** upsert on Java 21 **virtual threads**, write concern `majority`, **manual ack after Mongo ack** (see §8 rationale) |
| D5 | Mongo consistency | Idempotent upsert on deterministic `auditId` → effectively-once (no XA — impossible across Kafka+Mongo). On reprocess with changed rules, **last verdict wins** (upsert overwrites same `_id`) — intentional, not a bug |
| D6 | Rule storage | **MongoDB** = source of truth; **Redis** = distributed cache of active rule docs; compiled `Expression`s held in an in-JVM `RuleCache` (hot path). A REST + static-HTML CRUD UI writes Mongo, refreshes Redis, and publishes a Redis Pub/Sub `rules-changed` signal so running instances recompile their cache. _(v2.1 change — was: relational DB table. See HANDOFF.md for the build plan.)_ |
| D7 | Rule language | **SpEL**, one boolean expression per rule |
| D8 | Rule context root | **`Map<String,Object>`** built from the JSON event via Jackson (`objectMapper.convertValue(node, Map.class)`). Indexer syntax `['field']` resolves natively on `Map`. **Do not** use a raw `JsonNode` root (indexer fails without a custom accessor) |
| D9 | Rule combinator | **Model 4** — composite single-expression rules; cross-rule combinator is **any-match** only |
| D10 | SpEL security | `SimpleEvaluationContext.forReadOnlyDataBinding()` — rules are untrusted DB input |
| D11 | Audit completeness | MATCHED, UNMATCHED, and ERRORED all audited; one record per event |
| D12 | Topology branching | **StreamsBuilder DSL** (v2.1 — was raw Topology + named sinks): `source.process(RoutingProcessor)` (new Processor API, still sees record metadata for `auditId`) emits one keyed `RoutingResult`; the DSL splits it — `mapValues(auditJson).to(audit)` for every event, `filter(matched).mapValues(routedValue).to(target)` for matches. No `selectKey` repartition; no `EvaluationResult` on the wire. Lifecycle via `@EnableKafkaStreams` (auto-startup off; started by `PipelineStarter` after rules load) |
| D13 | Language / platform | Java 21, Spring Boot 3.x, spring-kafka 3.x (Boot BOM-managed) |
| D14 | Poison handling (audit consumer) | Retry w/ backoff on audit consumer, then route to audit DLT |
| D15 | JSON→Java type coercion | Rules assume well-typed JSON. Jackson yields `Integer`/`Long`/`Double`/`Boolean`/`String`; SpEL promotes across numerics. A field arriving as the wrong JSON type (e.g. `"1000"` string vs number) either evaluates false or throws → handled as ERRORED (per §5). Document expected types per rule |
| D16 | Source deserialization / parse failure | A malformed source event must **not** wedge the stream. Register a `DeserializationExceptionHandler` (log + skip or route) and wrap `JsonContextFactory.toRoot` so a parse failure becomes an **ERRORED** audit, never an uncaught throw on the stream thread |
| D17 | Source/target/audit value format | **JSON as String/bytes Serde** (no Avro/Schema Registry). Locked so deps + Serde wiring are settled before commit 1 |

---

## 2. OPEN decisions — resolve before/at coding time

- **O1 — Rule reload mechanism:** periodic scheduled refresh *vs.* triggered
  (actuator endpoint / control topic / DB change signal). _Default if unspecified:
  periodic refresh every 30s + a manual actuator refresh endpoint._
- **O2 — Spring Boot 3.x minor:** org policy. _Default: latest patched 3.x._
- **O3 — UNMATCHED retention:** full record per event vs. sampled/aggregated; add
  Mongo TTL index if high-volume. _Default: full record + TTL index on UNMATCHED._

> v1's O4 (ERRORED type) is now locked: **separate `auditType=ERRORED`** (see D11).
> v1's O5 (value format) is now locked as **D17**.

---

## 3. Dependencies (Maven coordinates, Boot-BOM managed)

- `org.springframework.boot:spring-boot-starter`
- `org.springframework.kafka:spring-kafka`
- `org.apache.kafka:kafka-streams`
- `org.springframework.boot:spring-boot-starter-data-mongodb`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-actuator`
- Test: `spring-kafka-test` (`TopologyTestDriver`), `de.flapdoodle.embed.mongo` or Testcontainers, `org.springframework.boot:spring-boot-starter-test`

> Do **not** hard-pin `spring-kafka` / Kafka client / Mongo driver versions — let
> the Spring Boot BOM resolve them. Confirm broker is 3.6+.

---

## 4. Package & class layout to create

```
com.example.ruleaudit
├─ RuleAuditApplication.java
├─ config/
│   ├─ KafkaStreamsConfig.java        // EOS props, Serdes, DeserializationExceptionHandler, StreamsBuilderFactoryBean customizer
│   ├─ MongoConfig.java               // MongoTemplate, write concern majority
│   ├─ VirtualThreadConfig.java       // virtual-thread executor for the audit consumer
│   └─ TopicsConfig.java              // NewTopic beans: target, audit, audit-DLT (RF per profile)
├─ rules/
│   ├─ Rule.java                      // Mongo document: id, description, spelExpression, active, updatedAt
│   ├─ RuleRepository.java            // MongoRepository, findByActiveTrue()
│   ├─ RuleRedisStore.java            // Jackson JSON cache in Redis
│   ├─ RuleService.java               // CRUD + Mongo/Redis sync + Pub/Sub signaling
│   ├─ RuleChangeListener.java        // Redis MessageListener -> RuleLoader.reload()
│   ├─ CompiledRule.java              // {id, Expression, description} cached form
│   ├─ RuleCache.java                 // AtomicReference<List<CompiledRule>>; reload(); thread-safe
│   └─ RuleLoader.java                // reads Redis (fallback Mongo) -> parses SpEL once -> populates RuleCache
├─ web/
│   └─ RuleController.java            // REST API at /api/rules
├─ eval/
│   ├─ RuleEvaluator.java            // core: eval all rules vs Map root, returns EvaluationResult
│   └─ EvaluationResult.java         // {matched, matchedRuleIds, evaluatedRuleIds, errors} — in-process only, no Serde
├─ topology/
│   └─ RoutingTopology.java          // builds the KStream: source -> single process() -> forward to target/audit sinks
├─ audit/
│   ├─ AuditRecord.java              // schema (see §6); includes schemaVersion; auditType enum MATCHED/UNMATCHED/ERRORED
│   ├─ AuditType.java
│   ├─ AuditKey.java                 // deterministic key: sha256(sourceTopic|partition|offset) or business key
│   ├─ AuditConsumer.java           // @KafkaListener (virtual threads) on audit topic -> synchronous upsert -> manual ack
│   ├─ AuditMongoRepository.java    // or direct MongoTemplate usage
│   └─ AuditDltConfig.java          // DefaultErrorHandler + DeadLetterPublishingRecoverer, backoff
└─ json/
    └─ JsonContextFactory.java       // bytes/String -> Map<String,Object> root for SpEL; parse failure -> ERRORED signal
```

---

## 5. SpEL evaluation — implementation rules (critical correctness)

1. **Parse once, eval many.** `RuleLoader` calls `parser.parseExpression(expr)`
   once per rule at load/reload and stores the `Expression` in `CompiledRule`.
   Never parse on the per-event hot path.
2. **Root is a `Map<String,Object>`.** `JsonContextFactory` converts the event
   JSON to a `Map` via Jackson. Build a
   `SimpleEvaluationContext.forReadOnlyDataBinding().build()` with that `Map` as
   root. Reuse the parser (thread-safe); build a fresh lightweight context per
   record.
3. **Indexer syntax** because root is a `Map`: expressions look like
   `['amount'] > 1000 and ['region'] == 'EU'`. Nested access: `['order']['total']`.
   Document this for rule authors. (This works on `Map`; it would **not** work on
   a raw `JsonNode` — see D8.)
4. **Type expectations (D15):** numeric comparisons assume numeric JSON values.
   SpEL promotes `Integer`/`Long`/`Double`. A wrong-typed field (string where a
   number is expected) either compares false or throws — both handled in step 6.
5. **Parse failures (D16):** if `JsonContextFactory.toRoot` cannot build the Map
   (malformed JSON), short-circuit to an **ERRORED** audit for that event. Never
   let the parse throw escape to the stream thread.
6. **Each rule wrapped in try/catch.** On `EvaluationException`/`SpelEvaluationException`,
   record the error in `EvaluationResult.errors`, treat that rule as non-match,
   and mark the event ERRORED. **Never** let a throw escape to the stream thread.
7. **Verdict = any-match:** `matched = !matchedRuleIds.isEmpty()`.
8. **Security:** `SimpleEvaluationContext` only. Never `StandardEvaluationContext`
   (would allow `T(java.lang.Runtime)...` RCE from DB-stored strings).

---

## 6. AuditRecord schema (one schema, one topic, one collection)

| Field | Type | Notes |
|---|---|---|
| `auditId` | String | Deterministic; Mongo `_id`; drives idempotent upsert |
| `schemaVersion` | int | For forward-compatible evolution of this record |
| `auditType` | enum | MATCHED / UNMATCHED / ERRORED |
| `matchedRuleIds` | List<String> | rules that fired |
| `evaluatedRuleIds` | List<String> | all active rules evaluated (forensics for UNMATCHED) |
| `routedEvent` | JSON | event emitted to target (MATCHED only) |
| `sourceEvent` | JSON | original event (or reference) |
| `errors` | Map/List | per-rule eval errors / parse error if any |
| `sourceTopic/partition/offset` | — | provenance |
| `timestamp` | Instant | event/processing time |

Serde: **JSON Serde** for the audit topic value. Index `auditType`; add TTL index
on UNMATCHED if O3 says so.

**Model 4 caveat to document in code comments:** a composite expression returning
false yields a single boolean — the UNMATCHED record can name the failed rule and
its expression + event values, but not which *clause* failed. Encourage small,
single-concern expressions to keep audits explanatory.

---

## 7. Topology shape (RoutingTopology.java)

```
source = builder.stream(sourceTopic, Consumed.with(keySerde, jsonStringSerde))

// Single process() node does everything — no selectKey repartition,
// no EvaluationResult on the wire.
source.process(() -> new RoutingProcessor(evaluator, ruleCache), ...)
//   RoutingProcessor.process(record):
//     root      = JsonContextFactory.toRoot(record.value())   // parse failure -> ERRORED audit only
//     result    = evaluator.evaluate(root)
//     auditId   = AuditKey.of(sourceTopic, partition, offset)
//     if result.matched:
//        context.forward(new Record(auditId, record.value(), ts), "target-sink")
//        context.forward(new Record(auditId, matchedAudit,     ts), "audit-sink")
//     else:
//        context.forward(new Record(auditId, unmatchedOrErroredAudit, ts), "audit-sink")
```

- The `process()` node forwards records with the **`auditId` key already set**
  directly to named sinks (`target-sink`, `audit-sink`). This avoids a
  `selectKey`-induced repartition topic and keeps matched/unmatched records for an
  event co-partitioned on the audit topic for idempotent downstream upsert.
- Both `target-sink` and `audit-sink` writes run inside the same EOS txn.
- `EvaluationResult` stays in-process (no Serde, never serialized across nodes).

### EOS / broker config
```properties
spring.kafka.streams.properties.processing.guarantee=exactly_once_v2
# prod profile:
spring.kafka.streams.replication.factor=3
# broker: min.insync.replicas=2 ; topics RF>=3
```

### Dev profile (single broker)
```properties
# application-dev.yml — one broker can't satisfy RF=3
spring.kafka.streams.replication.factor=1
# topic beans (TopicsConfig) read RF from a property so dev=1, prod=3
```
Use embedded Kafka / Testcontainers for tests; do not assume a 3-broker cluster
locally (step 1 of the build order would otherwise fail).

### Default deserialization exception handler (D16)
```properties
spring.kafka.streams.properties.default.deserialization.exception.handler=\
  org.apache.kafka.streams.errors.LogAndContinueExceptionHandler
```
Pair with the in-`process()` parse guard so structurally-valid-but-semantically-bad
events become ERRORED audits.

---

## 8. Audit consumer (AuditConsumer.java)

- Separate `@KafkaListener` (own group id, own concurrency); **not** part of the
  Streams app.
- **Why synchronous Mongo, not reactive (D4):** a blocking `@KafkaListener`
  container that must commit only after the Mongo ack would have to `.block()` on
  a reactive call anyway — gaining nothing. Use the synchronous `MongoTemplate`
  and run the listener on **Java 21 virtual threads** for I/O-bound concurrency.
  (Full reactive would require `reactor-kafka`'s `ReactiveKafkaConsumerTemplate`,
  not a `@KafkaListener` — out of scope.)
- For each record: `mongoTemplate` upsert keyed on `auditId`, write concern
  `majority`.
- **Manual ack** (`AckMode.MANUAL`/`MANUAL_IMMEDIATE`): ack only after the Mongo
  upsert returns successfully.
- Error handling: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with
  exponential backoff; after N attempts publish to `audit-DLT`.
- Backpressure is free: if Mongo is down, records pile up in the audit topic; the
  Streams app keeps routing; consumer catches up later.

---

## 9. Build order (suggested commits)

1. Project skeleton, deps, `application.yml` + `application-dev.yml`, topic beans
   (`TopicsConfig`, RF from property), JSON Serde wiring (D17).
2. `Rule` entity + repo + Flyway/Liquibase migration for `rule` table.
3. SpEL layer: `CompiledRule`, `RuleCache`, `RuleLoader`, `RuleEvaluator`,
   `EvaluationResult` + `JsonContextFactory` (Map root) + unit tests (match,
   non-match, eval-error, parse-failure, injection-attempt, wrong-type field).
4. `AuditRecord`/`AuditType`/`AuditKey`.
5. `RoutingTopology` (single `process()`) + `KafkaStreamsConfig` (EOS + deser
   handler) + `TopologyTestDriver` tests (routing to target + audit record for
   MATCHED / UNMATCHED / ERRORED).
6. `AuditConsumer` (virtual threads, manual ack) + `MongoConfig` + DLT config +
   embedded/Testcontainers Mongo test.
7. Rule reload (O1) + actuator endpoint; wire health checks.
8. End-to-end test: produce to source → assert target + Mongo audit docs.
9. Hardening: metrics (Micrometer), TTL index (O3), README.

---

## 10. Test checklist

- [ ] Rule evaluates true → MATCHED, routed event on target, MATCHED audit in Mongo.
- [ ] Rule evaluates false → no target message, UNMATCHED audit in Mongo.
- [ ] Malformed/throwing rule → stream survives, ERRORED audit written.
- [ ] **Malformed source JSON → stream survives, ERRORED audit written (D16).**
- [ ] **Wrong-typed field (string vs number) → handled, no stream crash (D15).**
- [ ] **`['field']` indexer resolves against Map root (D8) — nested access works.**
- [ ] SpEL injection string (`T(java.lang.Runtime)...`) in a DB rule → blocked by
      `SimpleEvaluationContext` (rule fails safely, no code execution).
- [ ] Reprocessing same offset → audit doc upserted, not duplicated (idempotency).
- [ ] Reprocess after rule change → same `auditId`, last verdict wins (D5).
- [ ] Mongo down → audit topic backs up, target routing unaffected, recovery drains.
- [ ] Poison audit record → lands in DLT after retries, pipeline not wedged.
- [ ] Rule reload picks up DB change without restart (per O1).

---

## 11. Quick reference — gotchas

- **`['field']` indexer needs a `Map` root, not `JsonNode`.** This is why D8
  converts to `Map<String,Object>` up front. A raw `JsonNode` silently fails the
  indexer unless you register a custom `PropertyAccessor`/`IndexAccessor`.
- No "Kafka 2.8.10"; original spring-kafka 2.8.x is **incompatible** with Java 21.
  Use Boot 3.x / spring-kafka 3.x.
- SpEL compiled mode gives little benefit here (values are `Object`); skip it.
- Kafka EOS does **not** cover the Mongo write — that's why the audit topic +
  idempotent upsert exist. Don't try to wrap a Mongo write in the topology to fake
  atomicity (kills throughput, still not atomic).
- **Don't put `EvaluationResult` on the wire** — keep it in the `process()` node;
  it has no Serde and forcing one would add a needless repartition.
- **Don't use the reactive Mongo starter for a blocking `@KafkaListener`** — it
  forces a `.block()`. Synchronous template + virtual threads (D4).
- `SimpleEvaluationContext`, always. DB-stored expressions are untrusted.
- RF=3 won't start on a single dev broker — use the dev profile (RF=1).
