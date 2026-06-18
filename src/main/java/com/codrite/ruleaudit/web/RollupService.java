package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.audit.AuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.MergeOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Maintains pre-aggregated analytics rollups so the dashboard never scans the
 * raw {@code audits} collection at query time.
 * <p>
 * Two hourly-bucketed collections are produced with idempotent {@code $merge}:
 * <ul>
 *     <li>{@code rule_rollups} keyed by {@code {hour, ruleId}} — matched/unmatched/
 *         errored counts plus latency sums and an evaluation count.</li>
 *     <li>{@code hour_rollups} keyed by {@code hour} — distinct messages processed.</li>
 * </ul>
 * Because each run recomputes whole buckets (rather than incrementing), re-running
 * over an overlapping window is safe — no double counting — so this is decoupled
 * from the exactly-once audit write path.
 */
@Slf4j
@Service
public class RollupService {

    static final String RULE_ROLLUPS = "rule_rollups";
    static final String HOUR_ROLLUPS = "hour_rollups";
    /** Truncates a timestamp to the start of its UTC hour as an ISO-8601 string. */
    private static final String HOUR_FORMAT = "%Y-%m-%dT%H:00:00Z";

    /** Redis leader-lock keys so only one app instance runs each rollup. */
    private static final String ROLLUP_LOCK = "analytics:rollup:lock";
    private static final String BACKFILL_LOCK = "analytics:rollup:backfill:lock";

    private final ReactiveMongoTemplate mongoTemplate;
    private final ReactiveStringRedisTemplate redis;
    private final int recomputeHours;
    private final int backfillHours;
    private final long intervalMs;
    private final boolean enabled;
    /** Unique per-instance owner token written into the lock value. */
    private final String instanceId = UUID.randomUUID().toString();

    public RollupService(ReactiveMongoTemplate mongoTemplate,
                         ReactiveStringRedisTemplate redis,
                         @Value("${app.analytics.rollup.recompute-hours:3}") int recomputeHours,
                         @Value("${app.analytics.rollup.backfill-hours:26}") int backfillHours,
                         @Value("${app.analytics.rollup.interval-ms:60000}") long intervalMs,
                         @Value("${app.analytics.rollup.enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.redis = redis;
        this.recomputeHours = recomputeHours;
        this.backfillHours = backfillHours;
        this.intervalMs = intervalMs;
        this.enabled = enabled;
    }

    /**
     * One-time backfill on startup so existing audit data is queryable immediately.
     * A longer-lived lock keeps the (heavier) backfill on a single instance.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!enabled) {
            return;
        }
        redis.opsForValue().setIfAbsent(BACKFILL_LOCK, instanceId, Duration.ofMinutes(10))
                .onErrorReturn(true) // Redis unavailable -> fail open so the backfill still happens
                .flatMap(acquired -> {
                    if (!Boolean.TRUE.equals(acquired)) {
                        log.info("Analytics rollup backfill skipped (another instance owns the lock)");
                        return Mono.<Void>empty();
                    }
                    log.info("Backfilling analytics rollups for the last {}h", backfillHours);
                    return rebuildSince(Instant.now().minus(backfillHours, ChronoUnit.HOURS))
                            .doOnSuccess(v -> log.info("Analytics rollup backfill complete"));
                })
                .onErrorResume(error -> {
                    log.error("Analytics rollup backfill failed", error);
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * Periodically recomputes the recent buckets. Only a small trailing window is
     * recomputed; older buckets were finalized when they were recent. Blocks so
     * the fixed-delay schedule never overlaps a still-running pass.
     * <p>
     * A Redis lock ensures only one instance runs per tick. The lock auto-expires
     * after the interval, so the next tick is a fresh race and a crashed leader is
     * transparently replaced.
     */
    @Scheduled(fixedDelayString = "${app.analytics.rollup.interval-ms:60000}",
               initialDelayString = "${app.analytics.rollup.interval-ms:60000}")
    public void scheduledRollup() {
        if (!enabled) {
            return;
        }
        if (!acquireRollupLock()) {
            log.debug("Scheduled analytics rollup skipped (another instance owns the lock)");
            return;
        }
        try {
            rebuildSince(Instant.now().minus(recomputeHours, ChronoUnit.HOURS)).block();
        } catch (Exception e) {
            log.error("Scheduled analytics rollup failed", e);
        }
    }

    /**
     * Atomic {@code SET NX PX} so exactly one instance wins each tick. Fails open
     * (returns {@code true}) if Redis is unavailable, so a Redis outage degrades to
     * per-instance rollups rather than to none.
     */
    private boolean acquireRollupLock() {
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(ROLLUP_LOCK, instanceId, Duration.ofMillis(intervalMs))
                    .block();
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Rollup lock unavailable, running without it: {}", e.getMessage());
            return true;
        }
    }

    private Mono<Void> rebuildSince(Instant from) {
        AggregationOptions diskUse = AggregationOptions.builder().allowDiskUse(true).build();
        Criteria window = Criteria.where("timestamp").gte(from);

        // rule_rollups: counts + latency sums per (hour, ruleId)
        Aggregation ruleAgg = Aggregation.newAggregation(
                Aggregation.match(window),
                Aggregation.project("ruleId", "parseTimeNano", "evalTimeNano", "totalTimeNano")
                        .and("timestamp").dateAsFormattedString(HOUR_FORMAT).as("hour")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("MATCHED")).then(1).otherwise(0)).as("isMatched")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("UNMATCHED")).then(1).otherwise(0)).as("isUnmatched")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("ERRORED")).then(1).otherwise(0)).as("isErrored"),
                Aggregation.group("hour", "ruleId")
                        .sum("isMatched").as("matched")
                        .sum("isUnmatched").as("unmatched")
                        .sum("isErrored").as("errored")
                        .count().as("count")
                        .sum("parseTimeNano").as("parseSum")
                        .sum("evalTimeNano").as("evalSum")
                        .sum("totalTimeNano").as("totalSum"),
                MergeOperation.builder().intoCollection(RULE_ROLLUPS).build()
        ).withOptions(diskUse);

        // hour_rollups: distinct (sourceTopic, partition, offset) per hour
        Aggregation messageAgg = Aggregation.newAggregation(
                Aggregation.match(window),
                Aggregation.project("sourceTopic", "partition", "offset")
                        .and("timestamp").dateAsFormattedString(HOUR_FORMAT).as("hour"),
                Aggregation.group("hour", "sourceTopic", "partition", "offset"),
                Aggregation.group("_id.hour").count().as("messages"),
                MergeOperation.builder().intoCollection(HOUR_ROLLUPS).build()
        ).withOptions(diskUse);

        return mongoTemplate.aggregate(ruleAgg, AuditRecord.class, Document.class)
                .then(mongoTemplate.aggregate(messageAgg, AuditRecord.class, Document.class).then());
    }
}
