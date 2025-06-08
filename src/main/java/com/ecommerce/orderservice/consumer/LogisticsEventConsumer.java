package com.ecommerce.orderservice.consumer;

import com.ecommerce.orderservice.event.LogisticsEvent;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogisticsEventConsumer {
    private final OrderService orderService;

    @KafkaListener(
            topics = "LOGISTICS_EVENTS",
            groupId = "order-service-group",
            containerFactory = "logisticsKafkaListenerContainerFactory"
    )
    public void handleLogisticsEvent(LogisticsEvent event) {
        if (event == null || event.getOrderId() == null || event.getStatus() == null) {
            log.error("Invalid logistics event received: {}", event);
            return;
        }
        log.info("Received logistics event: {}", event);
        try {
            orderService.handleLogisticsEvent(event);
        } catch (Exception e) {
            log.error("Error processing logistics event: {}", event, e);
        }
    }
}