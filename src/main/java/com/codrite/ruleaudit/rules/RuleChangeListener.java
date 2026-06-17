package com.codrite.ruleaudit.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RuleChangeListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RuleChangeListener.class);

    private final RuleLoader ruleLoader;

    public RuleChangeListener(RuleLoader ruleLoader) {
        this.ruleLoader = ruleLoader;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Received rule change notification from Redis pub/sub");
        ruleLoader.reload();
    }
}
