package com.codrite.ruleaudit.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating deterministic audit identifiers.
 */
public final class AuditKey {
    /**
     * Private constructor to prevent instantiation.
     */
    private AuditKey() {
    }

    /**
     * Generates a unique, deterministic ID for a specific rule evaluation on a Kafka record.
     * 
     * @param topic     The name of the source topic.
     * @param partition The partition number.
     * @param offset    The record offset.
     * @param ruleId    The ID of the rule being evaluated.
     * @return A hex-encoded SHA-256 string.
     */
    public static String of(String topic, int partition, long offset, String ruleId) {
        String input = topic + "|" + partition + "|" + offset + "|" + ruleId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a unique, deterministic ID for a Kafka record (generic event).
     * 
     * @param topic     The name of the source topic.
     * @param partition The partition number.
     * @param offset    The record offset.
     * @return A hex-encoded SHA-256 string.
     */
    public static String of(String topic, int partition, long offset) {
        return of(topic, partition, offset, "_event");
    }
}
