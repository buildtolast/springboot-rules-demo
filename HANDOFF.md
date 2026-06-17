# HANDOFF — next slice: Redis-cached, Mongo-persisted rules + CRUD UI (springboot-rules-demo)

Pick this up in a fresh session. Context you need is here + `CLAUDE.md` +
`IMPLEMENTATION_CONTEXT_V2.md` + `specs/`. Follow the local-LLM loop workflow in
`~/.claude/rules/common/local-llm-workflow.md` (Opus specs/reviews; local LLM
generates via `tools/llm-loop.py` / `tools/llm-gen.sh`; needs `UNSLOTH_API_KEY`).

## Current working state (verified)
- Full Kafka+Mongo pipeline runs E2E: `docker compose up --build` (the `demo`
  profile produces 25 msgs → logs `DEMO RESULTS`: 13 MATCHED / 6 UNMATCHED / 6
  ERRORED; 25 Mongo audit docs). EOS confirmed (txn commit markers on
  target/audit topics).
- Topology uses the **StreamsBuilder DSL** (`config/KafkaStreamsConfig`,
  `@EnableKafkaStreams`): `source.process(RoutingProcessor)` emits a keyed
  `topology/RoutingResult`, split by the DSL into target (`filter(matched)`) and
  audit (every event) sinks. Streams auto-startup is off; `PipelineStarter`
  starts the `StreamsBuilderFactoryBean` after `RuleLoader.reload()`.
- Rules TODAY: **H2 + JPA** (`Rule` @Entity, `RuleRepository` JpaRepository),
  seeded by `RuleSeeder` (22 SpEL rules), loaded once at startup by `RuleLoader`
  into the in-JVM `RuleCache` (compiled `Expression`s — the hot path used by
  `topology/RoutingProcessor`).
- Audit already persists to Mongo via `AuditConsumer` + `MongoConfig`
  (write-concern majority). `ObjectMapper` is defined in `config/AppConfig`
  (JSR-310). Streams wiring in `config/KafkaStreamsConfig`; started by
  `config/PipelineStarter`.

## Task
Replace the rule store and add an admin UI:
- **Mongo = source of truth** for rules (drop H2/JPA for rules).
- **Redis = distributed cache** of the active rule documents.
- **CRUD UI** (REST API + one static HTML/JS page) that writes Mongo, refreshes
  Redis, and signals the running Streams app to recompile its in-JVM cache.

## Locked design decisions (from user)
- **UI:** add `spring-boot-starter-web`; `@RestController` at `/api/rules`
  (list/get/create/update/delete); serve one vanilla HTML+JS page from
  `src/main/resources/static/` (fetch-based CRUD). No JS build toolchain.
- **Propagation:** **Redis Pub/Sub.** On any mutation: write Mongo → refresh
  Redis → publish to a Redis channel (e.g. `rules-changed`). A subscriber in the
  app reloads the in-JVM `RuleCache` from Redis (recompile). Correct across
  multiple instances.
- **Layering:** Mongo (truth) → Redis (cached active rule docs, Jackson JSON) →
  in-JVM `RuleCache` (compiled `Expression`s, hot path). `RuleCache` stays as-is;
  it is refreshed, not replaced (SpEL `Expression`s aren't Redis-serializable).

## Implementation plan
**Bootstrap/config (Opus writes):**
1. `build.gradle.kts`: remove `spring-boot-starter-data-jpa` + `com.h2database:h2`;
   add `spring-boot-starter-web` and `spring-boot-starter-data-redis`.
2. `application.yml`: remove the `spring.datasource`/`jpa` blocks; add
   `spring.data.redis.host=${REDIS_HOST:localhost}` / `port: 6379`.
3. `docker-compose.yml`: add a `redis:7` service (+ healthcheck) and
   `REDIS_HOST: redis` in the app env; `depends_on` redis healthy.
4. **ObjectMapper conflict:** adding spring-web makes Spring auto-configure an
   ObjectMapper. Reconcile with the manual `@Bean objectMapper` in `AppConfig`
   (mark it `@Primary`, or remove ours and instead customize via a
   `Jackson2ObjectMapperBuilderCustomizer` registering JavaTimeModule). Verify
   `Instant` still serializes as ISO.

**Logic (local LLM via loop, with a `specs/_contract3.md` of signatures):**
5. `rules/Rule.java` → Mongo `@Document("rule")`, `@Id String id`, description,
   spelExpression, active, updatedAt (Instant). (Replaces the JPA entity.)
6. `rules/RuleRepository.java` → `MongoRepository<Rule,String>` +
   `List<Rule> findByActiveTrue()`. (Replaces JpaRepository.)
7. `rules/RuleRedisStore.java` → read/write the active-rule list in Redis
   (Jackson JSON via `StringRedisTemplate`; key e.g. `rules:active`). Load from
   Mongo + populate on miss.
8. `rules/RuleService.java` → CRUD; each mutation: save Mongo → refresh Redis →
   publish `rules-changed`.
9. `rules/RuleChangeListener.java` → Redis `MessageListener` on `rules-changed`
   that calls `RuleLoader.reload()`.
10. Update `rules/RuleLoader.java` → `reload()` reads from `RuleRedisStore`
    (not the JPA repo), compiles, `RuleCache.replace(...)`.
11. `web/RuleController.java` → `@RestController` `/api/rules` delegating to
    `RuleService`. DTO record for create/update payloads.
12. `config/RuleSeeder.java` → seed the 22 rules into Mongo via `RuleService`
    /repository if empty (keep the same 22 expressions already in the file).
13. `config/RedisConfig.java` (Opus) → `RedisMessageListenerContainer` wiring the
    listener to the channel; `StringRedisTemplate` is auto-configured.

**UI:**
14. `src/main/resources/static/rules.html` (+ inline JS) → table of rules with
    create/edit/delete calling `/api/rules`.

**Tests first (TDD, per workflow):**
15. `RuleService` unit tests (mock repo + redis store + publisher: verify
    mutation → save + refresh + publish). `RuleController` MockMvc tests.
    `RuleRedisStore` round-trip (Testcontainers Redis or mock). Generate tests
    single-shot, confirm RED, then impl via loop to GREEN.

## Verify (E2E)
- `docker compose up --build`. Open the UI, create a NEW rule that only a crafted
  message matches; produce that message to `source-events` (or via a small curl
  to a test endpoint / kafka console producer); confirm it becomes MATCHED in
  Mongo audits WITHOUT restarting the app (proves pub/sub refresh).
- Confirm Redis has the cached active-rule list (`redis-cli get rules:active`).
- Confirm rule docs exist in Mongo `rule` collection.

## Also update
- `IMPLEMENTATION_CONTEXT_V2.md` D6 has been changed to: rules persisted in
  **MongoDB**, cached in **Redis**, compiled in-JVM; CRUD UI refreshes Redis +
  pub/sub. (Already noted there.)

## Gotchas carried from this session
- One fenced block per file; never redefine sibling classes; the self-fix loop
  edits only its target file (generate multi-file single-shot, then loop one
  offender).
- Records + Jackson + Spring Data Mongo all work; `-parameters` is on (Boot).
- Local server reports 0 tokens (expected); run `tools/token-report.py` after.
- Gradle launcher pinned to JDK 21 via `gradle.properties` (host default is JDK
  26 which Gradle 8.10.2 can't run on); `.dockerignore` excludes it.
