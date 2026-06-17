package com.codrite.ruleaudit.web;

import org.springframework.data.annotation.Id;

/**
 * DTO for per-rule evaluation statistics.
 * The id field is populated from the _id in MongoDB aggregation.
 */
public record RuleStats(
    @Id String ruleId,
    long matched,
    long unmatched,
    long errored
) {}
