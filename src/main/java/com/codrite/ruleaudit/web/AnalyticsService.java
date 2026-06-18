package com.codrite.ruleaudit.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Serves analytics for the dashboard from pre-aggregated rollups maintained by
 * {@link RollupService}, so queries never scan the raw {@code audits} collection.
 * <p>
 * Rollups are hourly buckets, so range queries are accurate to the hour and lag
 * the live stream by at most the rollup interval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ReactiveMongoTemplate mongoTemplate;

    /** Matches the {@code %Y-%m-%dT%H:00:00Z} hour key the rollups are bucketed on. */
    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00'Z'").withZone(ZoneOffset.UTC);

    /**
     * Retrieves statistics for the given time range, read entirely from the
     * rollup collections.
     *
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @return A Mono containing the aggregated statistics.
     */
    public Mono<AnalyticsStats> getStats(Instant from, Instant to) {
        // Hour keys are ISO-8601 UTC strings, so lexical range == chronological range.
        String fromHour = HOUR_FMT.format(from.truncatedTo(ChronoUnit.HOURS));
        String toHour = HOUR_FMT.format(to.truncatedTo(ChronoUnit.HOURS));
        AggregationOptions diskUse = AggregationOptions.builder().allowDiskUse(true).build();

        // rule_rollups are keyed by _id = { hour, ruleId }; hour_rollups by _id = hour.
        Criteria ruleRange = Criteria.where("_id.hour").gte(fromHour).lte(toHour);
        Criteria hourRange = Criteria.where("_id").gte(fromHour).lte(toHour);

        // Per-rule breakdown: all rules, sorted by matched desc (UI paginates).
        Aggregation ruleStatsAgg = Aggregation.newAggregation(
                Aggregation.match(ruleRange),
                Aggregation.group("_id.ruleId")
                        .sum("matched").as("matched")
                        .sum("unmatched").as("unmatched")
                        .sum("errored").as("errored"),
                Aggregation.project("matched", "unmatched", "errored").and("_id").as("ruleId"),
                Aggregation.sort(Sort.Direction.DESC, "matched")
        ).withOptions(diskUse);
        Mono<List<RuleStats>> ruleStatsMono = mongoTemplate
                .aggregate(ruleStatsAgg, RollupService.RULE_ROLLUPS, RuleStats.class)
                .collectList();

        // Hourly time series, read straight from the rollups (no per-rule recompute).
        Aggregation timeSeriesAgg = Aggregation.newAggregation(
                Aggregation.match(ruleRange),
                Aggregation.project("matched", "unmatched", "errored")
                        .and("_id.hour").as("timestamp")
                        .and("_id.ruleId").as("ruleId"),
                Aggregation.sort(Sort.Direction.ASC, "timestamp")
        ).withOptions(diskUse);
        Mono<List<TimeSeriesPoint>> timeSeriesMono = mongoTemplate
                .aggregate(timeSeriesAgg, RollupService.RULE_ROLLUPS, TimeSeriesPoint.class)
                .collectList();

        // Rule executions = sum of per-bucket evaluation counts.
        Aggregation evalAgg = Aggregation.newAggregation(
                Aggregation.match(ruleRange),
                Aggregation.group().sum("count").as("total")
        ).withOptions(diskUse);
        Mono<Long> totalEvaluationsMono = mongoTemplate
                .aggregate(evalAgg, RollupService.RULE_ROLLUPS, CountResult.class)
                .next().map(CountResult::total).defaultIfEmpty(0L);

        // Messages processed = sum of per-hour distinct message counts.
        Aggregation messagesAgg = Aggregation.newAggregation(
                Aggregation.match(hourRange),
                Aggregation.group().sum("messages").as("total")
        ).withOptions(diskUse);
        Mono<Long> totalMessagesMono = mongoTemplate
                .aggregate(messagesAgg, RollupService.HOUR_ROLLUPS, CountResult.class)
                .next().map(CountResult::total).defaultIfEmpty(0L);

        // Latency: weighted average from summed nanos / counts.
        Aggregation latencyAgg = Aggregation.newAggregation(
                Aggregation.match(ruleRange),
                Aggregation.group()
                        .sum("parseSum").as("parseSum")
                        .sum("evalSum").as("evalSum")
                        .sum("totalSum").as("totalSum")
                        .sum("count").as("count")
        ).withOptions(diskUse);
        Mono<LatencyStats> latencyMono = mongoTemplate
                .aggregate(latencyAgg, RollupService.RULE_ROLLUPS, LatencySums.class)
                .next()
                .map(s -> {
                    long c = s.count();
                    if (c <= 0) {
                        return new LatencyStats(0, 0, 0);
                    }
                    return new LatencyStats((double) s.parseSum() / c, (double) s.evalSum() / c, (double) s.totalSum() / c);
                })
                .defaultIfEmpty(new LatencyStats(0, 0, 0));

        return Mono.zip(totalMessagesMono, totalEvaluationsMono, ruleStatsMono, timeSeriesMono, latencyMono)
                .map(tuple -> new AnalyticsStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        (long) tuple.getT5().avgParseTime,
                        (long) tuple.getT5().avgEvalTime,
                        (long) tuple.getT5().avgTotalTime
                ));
    }

    private record CountResult(long total) {}
    private record LatencySums(long parseSum, long evalSum, long totalSum, long count) {}
    private record LatencyStats(double avgParseTime, double avgEvalTime, double avgTotalTime) {}
}
