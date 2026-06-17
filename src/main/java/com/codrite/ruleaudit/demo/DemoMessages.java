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
        for (int i = 0; i < count; i++) {
            int index = i % 4;
            switch (index) {
                case 0 -> {
                    // MATCHED template (high amount / EU)
                    int amount = 2000 + (i % 5) * 1000;
                    messages.add(String.format(
                        "{\"amount\":%d,\"region\":\"EU\",\"tier\":\"standard\",\"flagged\":false}",
                        amount
                    ));
                }
                case 1 -> {
                    // MATCHED template (premium / flagged)
                    messages.add("{\"amount\":150,\"region\":\"US\",\"tier\":\"premium\",\"flagged\":true}");
                }
                case 2 -> {
                    // UNMATCHED template (matches none of the rules)
                    messages.add("{\"amount\":150,\"region\":\"LATAM\",\"tier\":\"gold\",\"flagged\":false}");
                }
                case 3 -> {
                    // MALFORMED (drives ERRORED)
                    if (i % 2 == 0) {
                        messages.add("{bad json");
                    } else {
                        messages.add("{\"amount\":}");
                    }
                }
            }
        }
        return messages;
    }
}
