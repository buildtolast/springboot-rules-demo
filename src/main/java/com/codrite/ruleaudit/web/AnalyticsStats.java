package com.codrite.ruleaudit.web;

import java.util.List;

/**
 * DTO for aggregate analytics statistics.
 */
public record AnalyticsStats(
    long totalMessages,
    long totalEvaluations,
    List<RuleStats> ruleStats
) {}
