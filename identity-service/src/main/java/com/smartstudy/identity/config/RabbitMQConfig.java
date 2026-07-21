package com.smartstudy.identity.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "user.events";
    public static final String QUEUE_NAME = "user.deleted.queue";
    public static final String ROUTING_KEY = "user.deleted";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue userDeletedQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding bindingUserDeleted(Queue userDeletedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(userEventsExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
