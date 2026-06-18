# Spec: ARCHITECTURE_EVOLUTION.md (polished, with visuals)

## Goal
Rewrite `ARCHITECTURE_EVOLUTION.md` into a polished, accurate architecture review that
tells the story of how this system went from a single-node V0 to the current
distributed, high-throughput design. Audience: senior engineers / architects reviewing
the design. Tone: precise, evidence-based, no marketing fluff.

## Output
- ONE Markdown document. No commentary before/after.
- GitHub-flavored Markdown. Use ```mermaid``` fenced blocks for all diagrams.
- Length target: 250-400 lines.

## HARD ACCURACY CONSTRAINTS (do not invent numbers)
- The workload is a VOLUME problem, not a rate claim. State it as:
  "100k input events x ~100 active rules = ~10M audit records per run."
  Do NOT write "100k+ msg/sec" or any unverified throughput rate.
- Fan-out happens in the Kafka Streams topology (RoutingProcessor): for each source
  event it evaluates ALL active rules and emits ONE AuditRecord per rule. Matched rules
  additionally produce a routed event.
- The Streams processing thread NEVER writes to Mongo. It forwards:
    - routed/matched events -> `target-events` topic
    - one audit JSON per rule -> `audit-events` topic
  A SEPARATE consumer group (`audit-writer`) drains `audit-events` into Mongo. This
  topic-based decoupling is the "async write / don't block the Kafka thread" mechanism.
- Evidence chain to cite (from real MongoDB slow-query logs):
    - V0 per-record write: writeConcern w:majority, waitForWriteConcernDurationMillis ~111ms
      PER record (the whole 112ms "slow query" was replication wait, cpu ~0.15ms).
    - Current batched bulk write: ordered:false, 500 ops in one command,
      waitForWriteConcurrencyDurationMillis ~36ms ONCE for the batch,
      durationMillis ~109ms total => ~0.22ms/record. ~500x reduction in per-record
      write-concern wait. Measured ~4,600 audit-writes/sec per consumer thread.
- Idempotency: audit _id is deterministic `topic:partition:offset:ruleId` (AuditKey),
  so bulk upserts are safe to retry; redelivery of an un-acked batch re-upserts, no dups.
- Write concern stays w:majority in the current design (batching made it affordable).

## DEPLOYMENT FACTS (from docker-compose.yml / run.sh / application.yml)
- TWO deployment profiles in one compose file:
    - Single-node (dev / "V0"): services `kafka` (single KRaft node, RF=1, min ISR=1),
      `mongo` (standalone), `redis`. APP_REPLICATION_FACTOR=1.
    - Cluster (scaled / current): `kafka-1/2/3` (KRaft combined broker+controller,
      QUORUM_VOTERS across all 3, OFFSETS/TXN_STATE RF=3, TXN min ISR=2),
      `mongo-1/2/3` (replica set `rs0`), `redis`. APP_REPLICATION_FACTOR=3.
- App: `app` service, `deploy.replicas: ${APP_REPLICAS:-10}` (default 10 instances),
  per-JVM heap bounded `-Xmx512m -Xms256m`, container mem limit 2G. Dynamic published
  port (10 replicas coexist).
- Topics: source=source-events, target=target-events, audit=audit-events,
  audit-dlt=audit-events.DLT. `app.partitions: 20`.
- Kafka Streams: application-id rule-audit-streams, processing.guarantee=exactly_once_v2,
  num.stream.threads=1, commit.interval.ms=1000, isolation.level=read_committed on the
  audit consumer.
- Consumer batch tuning (audit-writer): max.poll.records=500, fetch.min.bytes=1MiB,
  fetch.max.wait.ms=200, enable-auto-commit=false, ack-mode=manual_immediate. Dedicated
  batch ConcurrentKafkaListenerContainerFactory with a bounded DefaultErrorHandler
  (FixedBackOff 1s x5).
- MongoDB: WiredTiger cache `--wiredTigerCacheSizeGB ${MONGO_WT_CACHE_GB:-4}`, container
  limit raised for cluster. MongoTemplate carries WriteConcern.MAJORITY.
- Networking: containers use `host.docker.internal` to reach host-mapped ports; run.sh
  probes free ports and builds MONGODB_URI=...replicaSet=rs0 in cluster mode.
- Analytics rollups: RollupService recomputes hourly/rule buckets every 60s using Mongo
  $merge into rule_rollups / hour_rollups; a Redis lock elects a single instance to run
  rollups (leader election) so the 10 replicas don't duplicate work. Dashboard reads the
  small rollup collections instead of scanning raw audits.

## REQUIRED STRUCTURE
1. Title + 2-3 sentence executive summary (volume framing: 100k x 100 = 10M).
2. "The Workload" section: define the fan-out math and why audits, not events, are the
   pressure point. Include a mermaid flowchart of fan-out (1 event -> N rules -> N audits
   + M routed events).
3. "V0: The Starting Point":
   - What it was (single Kafka, standalone Mongo, reactive per-record save, dashboard
     scanning raw audits, single instance).
   - mermaid diagram of V0 data flow.
   - Bottlenecks, each with the concrete cause. Include the 111ms-per-record slow-query
     evidence in a fenced text block or quote.
4. "The Evolution" — ordered steps, each: Problem -> Change -> Result. Cover:
   a. Topic-based decoupling (audit-events / target-events; Streams thread never blocks
      on Mongo).
   b. Batch consume + bulk write (500/poll, one w:majority wait), with before/after
      slow-query numbers and a mermaid sequence diagram of the batch write path.
   c. Idempotent bulk upserts + manual ack + bounded error handler.
   d. Pre-aggregated rollups + Redis leader election.
   e. Horizontal scale-out: 20 partitions, 10 app instances, consumer group balancing.
   f. Clustering for HA: 3-node KRaft (RF=3, ISR=2), 3-node Mongo replica set.
   g. Resource bounding: per-JVM heap caps, WiredTiger cache sizing.
5. "Current Architecture": mermaid diagram of the full cluster (3 Kafka, 3 Mongo, 10 app,
   Redis, UI), plus the topic topology. Prose on EOS, read_committed, manual ack.
6. "V0 vs Current" comparison table (nodes, partitions, instances, write model,
   analytics, write-concern cost per record, failure tolerance).
7. "Deployment & Networking": single-node vs cluster profiles, run.sh dynamic ports,
   host.docker.internal strategy, env knobs (APP_REPLICAS, MONGO_WT_CACHE_GB,
   APP_REPLICATION_FACTOR).
8. "Trade-offs & Limits": honest section — added end-to-end latency from batching/linger,
   batch-drop-after-5-retries behavior, at-least-once + idempotency (not exactly-once to
   Mongo), single-host Docker contention inflating replication acks, rollup staleness
   window (60s).
9. Short "Future Work" (optional): partition count vs instance count, DLQ wiring,
   sharding audits, TTL/archival.

## STYLE
- Use tables and mermaid liberally (the user explicitly asked for visuals).
- Every quantitative claim must trace to the facts above. If something isn't in this
  spec, do not assert a number for it.
- No "Prepared by" signature line.
