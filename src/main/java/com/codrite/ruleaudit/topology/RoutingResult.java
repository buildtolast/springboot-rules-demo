package com.codrite.ruleaudit.topology;

public record RoutingResult(boolean matched, String routedValue, String auditJson) {}
