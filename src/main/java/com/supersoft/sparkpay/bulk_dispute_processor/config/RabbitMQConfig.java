package com.supersoft.sparkpay.bulk_dispute_processor.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BULK_JOBS_QUEUE = "bulk.jobs";
    public static final String BULK_JOBS_DLQ = "bulk.jobs.dlq";
    public static final String BULK_JOBS_EXCHANGE = "bulk.jobs.exchange";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }

    @Bean
    public Queue bulkJobsQueue() {
        return QueueBuilder.durable(BULK_JOBS_QUEUE)
                .withArgument("x-dead-letter-exchange", BULK_JOBS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    @Bean
    public Queue bulkJobsDlq() {
        return QueueBuilder.durable(BULK_JOBS_DLQ).build();
    }

    @Bean
    public DirectExchange bulkJobsExchange() {
        return new DirectExchange(BULK_JOBS_EXCHANGE);
    }

    @Bean
    public Binding bulkJobsBinding() {
        return BindingBuilder.bind(bulkJobsQueue()).to(bulkJobsExchange()).with("job");
    }

    @Bean
    public Binding bulkJobsDlqBinding() {
        return BindingBuilder.bind(bulkJobsDlq()).to(bulkJobsExchange()).with("dlq");
    }
}
