# IMPLEMENTATION CONTEXT — Kafka Streams SpEL Rule-Matching & Async Mongo Audit

> **Purpose of this file.** This is the working context/memory for building the
> project. It captures every decision already made, what is still open, the
> package/class layout to create, and the order to build in. Hand this to a
> developer or coding agent and they should be able to start without re-deriving
> anything. Companion design doc: `kafka-streams-spel-audit-plan.md` (the *why*);
> this file is the *what to build*.

---

## 0. One-paragraph statement of the system

Consume JSON events from a **source** Kafka topic. For each event, evaluate a set
of **database-stored SpEL boolean rules** against the event. If **any** rule
matches (any-match), route the event to a **target** topic. **Every** event —
matched or not — produces an audit record to an internal **audit** topic, inside
the same exactly-once Kafka transaction as the routing. A **separate** consumer
drains the audit topic and writes audit records to **MongoDB asynchronously**
(idempotent upsert), with a dead-letter topic for poison messages.

---

## 1. Locked decisions (do not re-litigate without reason)

| # | Decision | Choice |
|---|---|---|
| D1 | Stream framework | Kafka Streams (not plain consumer/producer) |
| D2 | Processing guarantee | `exactly_once_v2` |
| D3 | Audit delivery | Audit payload produced to internal audit topic **inside** the EOS txn; separate consumer writes to Mongo |
| D4 | Mongo write style | **Async** via reactive driver (`ReactiveMongoTemplate`), upsert, write concern `majority` |
| D5 | Mongo consistency | Idempotent upsert on deterministic key → effectively-once (no XA/distributed txn — impossible across Kafka+Mongo) |
| D6 | Rule storage | Relational **DB** table, loaded + cached in app |
| D7 | Rule language | **SpEL**, one boolean expression per rule |
| D8 | Rule context root | **JsonNode / Map** (arbitrary JSON) → indexer syntax `['field']` |
| D9 | Rule combinator | **Model 4** — composite single-expression rules; cross-rule combinator is **any-match** only |
| D10 | SpEL security | `SimpleEvaluationContext.forReadOnlyDataBinding()` — rules are untrusted DB input |
| D11 | Audit completeness | Both MATCHED and UNMATCHED audited; one record per event |
| D12 | Topology branching | Evaluate inline + branch on verdict (no `split()` needed for any-match) |
| D13 | Language / platform | Java 21, Spring Boot 3.x, spring-kafka 3.x (Boot BOM-managed) |
| D14 | Poison handling | Retry w/ backoff on audit consumer, then route to audit DLT |

---

## 2. OPEN decisions — resolve before/at coding time

These are the only things not yet pinned. Pick and record the choice inline here
when decided.

- **O1 — Rule reload mechanism:** periodic scheduled refresh *vs.* triggered
  (actuator endpoint / control topic / DB change signal). _Default if unspecified:
  periodic refresh every 30s + a manual actuator refresh endpoint._
- **O2 — Spring Boot 3.x vs 4.x:** org policy. _Default: latest patched 3.x._
- **O3 — UNMATCHED retention:** full record per event vs. sampled/aggregated; add
  Mongo TTL index if high-volume. _Default: full record + TTL index on UNMATCHED._
- **O4 — ERRORED audits:** separate `auditType=ERRORED` vs. UNMATCHED+error flag.
  _Default: separate `ERRORED` type._
- **O5 — Source/target value format:** assume JSON (String/bytes) Serde. Confirm
  whether Avro/Schema Registry is in play (changes Serde + deps).

---

## 3. Dependencies (Maven coordinates, Boot-BOM managed)

- `org.springframework.boot:spring-boot-starter`
- `org.springframework.kafka:spring-kafka`
- `org.apache.kafka:kafka-streams`
- `org.springframework.boot:spring-boot-starter-data-mongodb-reactive`
- `org.springframework.boot:spring-boot-starter-data-jpa` (rule store) + JDBC driver
- `org.springframework.boot:spring-boot-starter-actuator` (health + rule refresh endpoint)
- Test: `spring-kafka-test` (`TopologyTestDriver`), `de.flapdoodle.embed.mongo` or Testcontainers, `org.springframework.boot:spring-boot-starter-test`

> Do **not** hard-pin `spring-kafka` / Kafka client / Mongo driver versions — let
> the Spring Boot BOM resolve them. Confirm broker is 3.6+.

---

## 4. Package & class layout to create

```
com.example.ruleaudit
├─ RuleAuditApplication.java
├─ config/
│   ├─ KafkaStreamsConfig.java        // EOS props, Serdes, StreamsBuilderFactoryBean customizer
│   ├─ MongoConfig.java               // reactive template, write concern majority
│   └─ TopicsConfig.java              // NewTopic beans: target, audit, audit-DLT (RF>=3)
├─ rules/
│   ├─ Rule.java                      // JPA entity: id, description, spelExpression, active, version, updatedAt
│   ├─ RuleRepository.java            // Spring Data repo, findByActiveTrue()
│   ├─ CompiledRule.java              // {id, Expression, description} cached form
│   ├─ RuleCache.java                 // holds compiled rules; reload(); thread-safe (volatile/AtomicReference)
│   ├─ RuleLoader.java                // reads DB -> parses SpEL once -> populates RuleCache (per O1)
│   └─ RuleRefreshEndpoint.java       // actuator @Endpoint to trigger reload (per O1)
├─ eval/
│   ├─ RuleEvaluator.java            // core: eval all rules vs JsonNode root, returns EvaluationResult
│   └─ EvaluationResult.java         // {matched:boolean, matchedRuleIds, evaluatedRuleIds, errors}
├─ topology/
│   └─ RoutingTopology.java          // builds the KStream: source -> eval -> branch -> to(target)/to(audit)
├─ audit/
│   ├─ AuditRecord.java              // schema (see §6); auditType enum MATCHED/UNMATCHED/ERRORED
│   ├─ AuditType.java
│   ├─ AuditKey.java                 // deterministic key: sha256(sourceTopic|partition|offset) or business key
│   ├─ AuditConsumer.java           // @KafkaListener on audit topic -> reactive upsert
│   ├─ AuditMongoRepository.java    // or direct ReactiveMongoTemplate usage
│   └─ AuditDltConfig.java          // DefaultErrorHandler + DeadLetterPublishingRecoverer, backoff
└─ json/
    └─ JsonContextFactory.java       // bytes/String -> JsonNode/Map root for SpEL
```

---

## 5. SpEL evaluation — implementation rules (critical correctness)

1. **Parse once, eval many.** `RuleLoader` calls `parser.parseExpression(expr)`
   once per rule at load/reload and stores the `Expression` in `CompiledRule`.
   Never parse on the per-event hot path.
2. **Context per event:** build a `SimpleEvaluationContext.forReadOnlyDataBinding().build()`
   with the parsed `JsonNode`/`Map` as root object. Reuse the parser (thread-safe);
   build a fresh lightweight context per record or use a root-object setter.
3. **Indexer syntax** because root is Map/JsonNode: expressions look like
   `['amount'] > 1000 and ['region'] == 'EU'`. Document this for rule authors.
4. **Each rule wrapped in try/catch.** On `EvaluationException`/`SpelEvaluationException`,
   record the error in `EvaluationResult.errors`, treat that rule as non-match,
   and (per O4) optionally mark the event ERRORED. **Never** let a throw escape to
   the stream thread.
5. **Verdict = any-match:** `matched = !matchedRuleIds.isEmpty()`.
6. **Security:** `SimpleEvaluationContext` only. Never `StandardEvaluationContext`
   (would allow `T(java.lang.Runtime)...` RCE from DB-stored strings).

---

## 6. AuditRecord schema (one schema, one topic, one collection)

| Field | Type | Notes |
|---|---|---|
| `auditId` | String | Deterministic; Mongo `_id`; drives idempotent upsert |
| `auditType` | enum | MATCHED / UNMATCHED / ERRORED |
| `matchedRuleIds` | List<String> | rules that fired |
| `evaluatedRuleIds` | List<String> | all active rules evaluated (forensics for UNMATCHED) |
| `routedEvent` | JSON | event emitted to target (MATCHED only) |
| `sourceEvent` | JSON | original event (or reference) |
| `errors` | Map/List | per-rule eval errors if any |
| `sourceTopic/partition/offset` | — | provenance |
| `timestamp` | Instant | event/processing time |

Index `auditType`; add TTL index on UNMATCHED if O3 says so.

**Model 4 caveat to document in code comments:** a composite expression returning
false yields a single boolean — the UNMATCHED record can name the failed rule and
its expression + event values, but not which *clause* failed. Encourage small,
single-concern expressions to keep audits explanatory.

---

## 7. Topology shape (RoutingTopology.java)

```
source = builder.stream(sourceTopic, Consumed.with(keySerde, jsonSerde))
parsed = source.mapValues(json -> JsonContextFactory.toRoot(json))
result = parsed.mapValues((k,v) -> evaluator.evaluate(v))   // EvaluationResult attached

// MATCHED branch: produce routed event AND audit
//   .filter(matched).to(targetTopic)  + build MATCHED AuditRecord -> to(auditTopic)
// UNMATCHED branch: build UNMATCHED AuditRecord -> to(auditTopic)
```

- Key every record with the deterministic `auditId` so matched/unmatched records
  for the same event stay co-partitioned and the downstream upsert is idempotent.
- Both `to(targetTopic)` and `to(auditTopic)` run inside the same EOS txn.
- Implementation note: easiest is to compute the routed event + audit record in a
  single `process()`/`transformValues` and forward to two sinks, or split the
  stream by verdict and attach sinks per branch.

### EOS / broker config
```properties
spring.kafka.streams.properties.processing.guarantee=exactly_once_v2
spring.kafka.streams.replication.factor=3
# broker: min.insync.replicas=2 ; topics RF>=3
```

---

## 8. Audit consumer (AuditConsumer.java)

- Separate `@KafkaListener` (own group id, own concurrency); **not** part of the
  Streams app. Java 21 virtual threads suit this I/O-bound work.
- For each record: `reactiveMongoTemplate.save()/upsert()` keyed on `auditId`,
  write concern `majority`.
- Commit offset only after Mongo ack (manual ack or container ack mode that waits
  on the reactive completion).
- Error handling: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with
  exponential backoff; after N attempts publish to `audit-DLT`.
- Backpressure is free: if Mongo is down, records pile up in the audit topic; the
  Streams app keeps routing; consumer catches up later.

---

## 9. Build order (suggested commits)

1. Project skeleton, deps, `application.yml`, topic beans (`TopicsConfig`).
2. `Rule` entity + repo + Flyway/Liquibase migration for `rule` table.
3. SpEL layer: `CompiledRule`, `RuleCache`, `RuleLoader`, `RuleEvaluator`,
   `EvaluationResult` + unit tests (match, non-match, error, injection-attempt).
4. `AuditRecord`/`AuditType`/`AuditKey` + `JsonContextFactory`.
5. `RoutingTopology` + `KafkaStreamsConfig` (EOS) + `TopologyTestDriver` tests
   (verify routing to target + audit record for both verdicts).
6. `AuditConsumer` + `MongoConfig` + DLT config + embedded/Testcontainers Mongo test.
7. Rule reload (O1) + actuator endpoint; wire health checks.
8. End-to-end test: produce to source → assert target + Mongo audit docs.
9. Hardening: metrics (Micrometer), TTL index (O3), README.

---

## 10. Test checklist

- [ ] Rule evaluates true → MATCHED, routed event on target, MATCHED audit in Mongo.
- [ ] Rule evaluates false → no target message, UNMATCHED audit in Mongo.
- [ ] Malformed/throwing rule → stream survives, ERRORED (or flagged) audit written.
- [ ] SpEL injection string (`T(java.lang.Runtime)...`) in a DB rule → blocked by
      `SimpleEvaluationContext` (rule fails safely, no code execution).
- [ ] Reprocessing same offset → audit doc upserted, not duplicated (idempotency).
- [ ] Mongo down → audit topic backs up, target routing unaffected, recovery drains.
- [ ] Poison audit record → lands in DLT after retries, pipeline not wedged.
- [ ] Rule reload picks up DB change without restart (per O1).

---

## 11. Quick reference — gotchas

- No "Kafka 2.8.10"; original spring-kafka 2.8.x is **incompatible** with Java 21.
  Use Boot 3.x / spring-kafka 3.x.
- Map/JsonNode root → **indexer** syntax `['x']`, not `x`. SpEL compiled mode gives
  little benefit here (values are `Object`).
- Kafka EOS does **not** cover the Mongo write — that's why the audit topic +
  idempotent upsert exist. Don't try to wrap a synchronous Mongo write in the
  topology to fake atomicity (kills throughput, still not atomic).
- `SimpleEvaluationContext`, always. DB-stored expressions are untrusted.
