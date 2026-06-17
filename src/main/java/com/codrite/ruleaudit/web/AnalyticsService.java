package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.audit.AuditRecord;
import com.codrite.ruleaudit.audit.AuditType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Service for aggregating analytics data from audit logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Retrieves statistics for all rules within a given time range.
     * 
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @return A Mono containing the aggregated statistics.
     */
    public Mono<AnalyticsStats> getStats(Instant from, Instant to) {
        Criteria rangeCriteria = Criteria.where("timestamp").gte(from).lte(to);

        // Aggregate rule stats
        Aggregation ruleStatsAgg = Aggregation.newAggregation(
                Aggregation.match(rangeCriteria),
                Aggregation.project("ruleId")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("MATCHED")).then(1).otherwise(0)).as("isMatched")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("UNMATCHED")).then(1).otherwise(0)).as("isUnmatched")
                        .and(ConditionalOperators.when(ComparisonOperators.Eq.valueOf("auditType").equalToValue("ERRORED")).then(1).otherwise(0)).as("isErrored"),
                Aggregation.group("ruleId")
                        .sum("isMatched").as("matched")
                        .sum("isUnmatched").as("unmatched")
                        .sum("isErrored").as("errored"),
                Aggregation.project("matched", "unmatched", "errored")
                        .and("_id").as("ruleId")
        );

        Mono<List<RuleStats>> ruleStatsMono = mongoTemplate.aggregate(ruleStatsAgg, AuditRecord.class, RuleStats.class)
                .collectList();

        // Total messages (unique topic+partition+offset)
        Aggregation totalMessagesAgg = Aggregation.newAggregation(
                Aggregation.match(rangeCriteria),
                Aggregation.group("sourceTopic", "partition", "offset"),
                Aggregation.group().count().as("totalCount"),
                Aggregation.project("totalCount").andExclude("_id")
        );

        Mono<Long> totalMessagesMono = mongoTemplate.aggregate(totalMessagesAgg, AuditRecord.class, GlobalCount.class)
                .next()
                .map(GlobalCount::totalCount)
                .defaultIfEmpty(0L);

        // Total evaluations
        Mono<Long> totalEvaluationsMono = mongoTemplate.count(new Query(rangeCriteria), AuditRecord.class);

        return Mono.zip(totalMessagesMono, totalEvaluationsMono, ruleStatsMono)
                .map(tuple -> new AnalyticsStats(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private record GlobalCount(long totalCount) {}
}
