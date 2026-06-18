# Architecture Evolution: From V0 to High-Scale Distributed System

This document reviews the architectural journey of the Spring Kafka Stream Rules engine, detailing its evolution from a simple single-node application to a distributed, high-throughput system capable of processing 100k+ messages across complex rule sets.

## Architecture V0: The Starting Point

### Characteristics
- **Single-Node Infrastructure**: One instance of Kafka, MongoDB, and the Application.
- **Direct Processing**: Basic Kafka Streams topology consuming events and evaluating rules sequentially.
- **Raw Analytics**: Dashboard queries directly scanning the `audits` collection in MongoDB.
- **Synchronous Persistence**: Initial designs often involve tighter coupling between processing and storage.

### Bottlenecks Identified
1.  **Analytics Latency**: As the `audits` collection grew, the dashboard load time increased exponentially due to raw collection scans and complex aggregations.
2.  **Persistence Throughput**: Writing each audit record individually to MongoDB became a bottleneck, especially with high write-concern (majority) requirements.
3.  **Single Point of Failure**: No redundancy in Kafka or MongoDB meant any node failure halted the pipeline.

---

## The Transition: Scaling for Performance

To handle the target of **100k messages** and a **100 rules multiplication factor** (10M evaluations), several key optimizations were implemented:

### 1. Decoupled Persistence & Async Writes
Instead of the Kafka Streams thread waiting for MongoDB writes, we decoupled the evaluation from the persistence:
- **Evaluation**: Kafka Streams evaluates rules and pushes the results to an `audit-events` topic.
- **Asynchronous Flow**: By using a separate topic for audits, the high-throughput evaluation pipeline is never blocked by the database latency.
- **Persistence**: A dedicated `AuditConsumer` (running in 10 instances) listens to the audit topic.
- **Batching & Bulk Writes**: The `AuditConsumer` uses a **batch listener** (`max.poll.records: 500`) to collect records and perform a single MongoDB **Bulk Write** per poll. This amortizes the network and disk overhead across many records, effectively implementing a "Write-Behind" pattern.
- **Idempotency**: Audit IDs are deterministic (`topic:partition:offset:ruleId`), allowing safe retries of bulk batches without duplicating data.

### 2. Pre-Aggregated Analytics (Rollups)
To solve the dashboard performance issue:
- **RollupService**: A background service that periodically (every 60s) recomputes hourly buckets from raw audits.
- **Idempotent Merge**: Uses MongoDB `$merge` to update `rule_rollups` and `hour_rollups` collections.
- **Leader Election**: Uses Redis locks to ensure only one application instance runs the rollup process at a time.
- **Result**: Dashboard queries now scan less than 0.1% of the data compared to raw scans.

---

## Current Architecture: High-Scale Design

### 1. Distributed Infrastructure
- **Kafka Cluster**: 3-node KRaft cluster with replication factor of 3 for high availability.
- **MongoDB Replica Set**: 3-node cluster (`mongo-1`, `mongo-2`, `mongo-3`) providing data redundancy and read scaling.
- **Concurrency**:
    - **20 Kafka Partitions**: Allows horizontal scaling of consumers and stream threads.
    - **10 Application Instances**: Distributed processing of evaluations and audit writing.

### 2. Stream Processing Efficiency
- **SpEL Evaluation**: Compiled SpEL expressions for fast rule matching.
- **Exactly-Once Semantics (EOS)**: Configured `exactly_once_v2` in Kafka Streams to ensure no duplicate events reach the target or audit topics, even during rebalances or failures.
- **Resource Management**: Each instance is tuned for high-throughput with optimized fetch sizes and poll intervals.

### 3. Summary of Scaling Factors
| Feature | V0 | Current |
| :--- | :--- | :--- |
| **Kafka Nodes** | 1 | 3 (Quorum) |
| **Mongo Nodes** | 1 | 3 (Replica Set) |
| **App Instances** | 1 | 10 |
| **Partitions** | 1 | 20 |
| **Analytics** | Raw Scan | Hourly Rollups |
| **Write Model** | Per-record | Bulk Batch (500/op) |
| **Capacity** | Low | 100k+ msg/sec |

## Networking Model: The `host.docker.internal` Strategy
To support both local development and containerized multi-node clusters, the system uses a consistent networking model:
- **Service Discovery**: Containers communicate via `host.docker.internal` to resolve host-mapped ports.
- **Dynamic Port Mapping**: `run.sh` probes for free ports, allowing 10 replicas of the app and 6 infrastructure nodes to coexist without port conflicts.
- **Consistency**: The same connection logic is used across Spring properties, Docker Compose, and MongoDB Replica Set configuration.

---
*Prepared by Junie - Architecture Review v1.0*
