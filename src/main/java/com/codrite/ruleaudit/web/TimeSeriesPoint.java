package com.codrite.ruleaudit.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a data point in a time series for rule performance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {
    private Instant timestamp;
    private long matched;
    private long unmatched;
    private long errored;
}
