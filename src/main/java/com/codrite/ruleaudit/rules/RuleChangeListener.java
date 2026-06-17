package com.codrite.ruleaudit.rules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener that handles rule change notifications.
 * <p>
 * When any application instance updates a rule, a message is broadcast.
 * This listener catches that message and triggers a local reload of the 
 * rules to ensure all nodes in the cluster stay synchronized.
 */
@Slf4j
@Component
public class RuleChangeListener implements MessageListener {

    private final RuleLoader ruleLoader;

    public RuleChangeListener(RuleLoader ruleLoader) {
        this.ruleLoader = ruleLoader;
    }

    /**
     * Callback invoked when a message is received on the monitored Redis channel.
     * 
     * @param message The Redis message (content is usually ignored, the signal is enough).
     * @param pattern The pattern that matched (if any).
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Received rule change notification from Redis pub/sub");
        // Trigger a fresh reload from the centralized Redis store into the local cache
        ruleLoader.reload();
    }
}
