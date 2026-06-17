package com.codrite.ruleaudit.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for generating synthetic JSON events for the demo profile.
 */
public final class DemoMessages {
    /**
     * Private constructor to prevent instantiation.
     */
    private DemoMessages() {
    }

    /**
     * Generates a list of JSON messages with varying characteristics to test 
     * different rule outcomes (Match, No Match, Error).
     * 
     * @param count The number of messages to generate.
     * @return A list of JSON strings.
     */
    public static List<String> generate(int count) {
        List<String> messages = new ArrayList<>(count);
        String[] regions = {"US", "EU", "APAC", "LATAM", "EMEA"};
        String[] tiers = {"premium", "gold", "vip", "standard", "basic"};
        String[] sources = {"web", "mobile", "api", "legacy", "partner"};

        for (int i = 0; i < count; i++) {
            int amount = 500 + (i * 500) % 50000;
            String region = regions[i % regions.length];
            String tier = tiers[i % tiers.length];
            String source = sources[i % sources.length];
            int priority = (i % 5) + 1;
            double taxRate = 0.05 + (i % 20) * 0.01;
            String timestamp = "2026-06-17T" + String.format("%02d:%02d:%02dZ", i % 24, (i * 7) % 60, (i * 13) % 60);

            String itemsJson;
            if (i % 2 == 0) {
                itemsJson = String.format("[{\"id\":\"it-%d\",\"price\":%d,\"tags\":[\"high-value\"]},{\"id\":\"it-%d\",\"price\":%d,\"tags\":[\"promo\"]}]",
                        i, 500 + (i * 10) % 1000, i + 1, 600 + (i * 5) % 1000);
            } else {
                itemsJson = String.format("[{\"id\":\"it-%d\",\"price\":%d,\"tags\":[\"standard\"]}]",
                        i, 100 + (i * 2) % 500);
            }

            messages.add(String.format(
                "{\"amount\":%d,\"region\":\"%s\",\"tier\":\"%s\",\"flagged\":%b,\"metadata\":{\"source\":\"%s\",\"priority\":%d,\"tax_rate\":%.2f},\"order\":{\"items\":%s,\"total_items\":%d},\"timestamp\":\"%s\"}",
                amount, region, tier, i % 3 == 0, source, priority, taxRate, itemsJson, (i % 3) + 1, timestamp
            ));
        }
        return messages;
    }
}
