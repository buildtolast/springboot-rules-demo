package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.rules.RuleChangeListener;
import com.codrite.ruleaudit.rules.RuleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                  MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(RuleService.CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RuleChangeListener listener) {
        return new MessageListenerAdapter(listener);
    }
}
