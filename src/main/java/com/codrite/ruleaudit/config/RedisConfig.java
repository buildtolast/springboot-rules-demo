package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.rules.RuleChangeListener;
import com.codrite.ruleaudit.rules.RuleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Configuration for Redis infrastructure, specifically for Pub/Sub messaging.
 * <p>
 * This setup allows the application to listen for rule change notifications 
 * and synchronize its local cache across multiple instances.
 */
@Configuration
public class RedisConfig {

    /**
     * Configures the message listener container that monitors the rule change channel.
     * 
     * @param connectionFactory Spring-managed Redis connection factory.
     * @param listenerAdapter   The adapter for the {@link RuleChangeListener}.
     * @return A configured RedisMessageListenerContainer.
     */
    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                  MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(RuleService.CHANNEL));
        return container;
    }

    /**
     * Adapts our {@link RuleChangeListener} to the Redis message listener interface.
     * 
     * @param listener The rule change listener component.
     * @return A MessageListenerAdapter.
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RuleChangeListener listener) {
        return new MessageListenerAdapter(listener);
    }
}
