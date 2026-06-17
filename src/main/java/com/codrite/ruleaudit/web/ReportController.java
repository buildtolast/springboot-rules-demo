package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.audit.AuditRecord;
import com.codrite.ruleaudit.audit.AuditType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * REST controller for report generation and data export.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Retrieves the top 10 records for a given type and date range.
     */
    @GetMapping("/top")
    public Flux<AuditRecord> getTopRecords(
            @RequestParam AuditType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        Instant start = from != null ? from : Instant.now().minus(24, ChronoUnit.HOURS);
        Instant end = to != null ? to : Instant.now();

        Query query = new Query(Criteria.where("auditType").is(type)
                .and("timestamp").gte(start).lte(end))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(10);

        return mongoTemplate.find(query, AuditRecord.class);
    }

    /**
     * Exports audit records as a CSV stream.
     */
    @GetMapping("/export")
    public Mono<ResponseEntity<Flux<String>>> exportCsv(
            @RequestParam AuditType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        Instant start = from != null ? from : Instant.now().minus(24, ChronoUnit.HOURS);
        Instant end = to != null ? to : Instant.now();

        String filename = String.format("audit_%s_%s.csv", type.name().toLowerCase(), Instant.now().getEpochSecond());

        Query query = new Query(Criteria.where("auditType").is(type)
                .and("timestamp").gte(start).lte(end))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"));

        String header = "AuditId,RuleId,Type,Timestamp,Reason,SourceTopic,Partition,Offset,ParseTimeNano,EvalTimeNano,TotalTimeNano,SourceEvent,RoutedEvent\n";

        Flux<String> csvFlux = Flux.concat(
                Flux.just(header),
                mongoTemplate.find(query, AuditRecord.class)
                        .map(this::toCsvLine)
        );

        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvFlux));
    }

    private String toCsvLine(AuditRecord record) {
        return String.format("%s,%s,%s,%s,\"%s\",%s,%d,%d,%d,%d,%d,\"%s\",\"%s\"\n",
                escapeCsv(record.auditId()),
                escapeCsv(record.ruleId()),
                record.auditType(),
                record.timestamp(),
                escapeCsv(record.reason()),
                escapeCsv(record.sourceTopic()),
                record.partition(),
                record.offset(),
                record.parseTimeNano(),
                record.evalTimeNano(),
                record.totalTimeNano(),
                escapeCsv(record.sourceEvent()),
                escapeCsv(record.routedEvent())
        );
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
