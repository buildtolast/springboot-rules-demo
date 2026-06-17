package com.codrite.ruleaudit.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * REST controller for analytics data.
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Retrieves statistics for the specified time range.
     * Default is the last 24 hours.
     * 
     * @param from Optional start time.
     * @param to   Optional end time.
     * @return Aggregated statistics.
     */
    @GetMapping("/stats")
    public Mono<AnalyticsStats> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        
        Instant start = from != null ? from : Instant.now().minus(24, ChronoUnit.HOURS);
        Instant end = to != null ? to : Instant.now();
        
        log.info("Fetching analytics from {} to {}", start, end);
        return analyticsService.getStats(start, end);
    }
}
