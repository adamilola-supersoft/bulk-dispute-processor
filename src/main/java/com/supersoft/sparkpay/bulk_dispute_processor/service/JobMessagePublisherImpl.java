package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobMessagePublisherImpl implements JobMessagePublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public void publishJobMessage(JobMessage jobMessage) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.BULK_JOBS_EXCHANGE, "job", jobMessage);
            log.info("Published job message for jobId: {}, sessionId: {}", 
                    jobMessage.getJobId(), jobMessage.getSessionId());
        } catch (Exception e) {
            log.error("Failed to publish job message for jobId: {}", jobMessage.getJobId(), e);
            throw new RuntimeException("Failed to publish job message", e);
        }
    }
}
