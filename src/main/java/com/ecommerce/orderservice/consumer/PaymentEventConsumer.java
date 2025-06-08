package com.ecommerce.orderservice.consumer;

import com.ecommerce.orderservice.event.PaymentEvent;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
    private final OrderService orderService;

    @KafkaListener(
            topics = "PAYMENT_EVENTS",
            groupId = "order-service-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(PaymentEvent event) {
        if (event == null || event.getOrderId() == null || event.getStatus() == null) {
            log.error("Invalid payment event received: {}", event);
            return;
        }
        log.info("Received payment event: {}", event);
        try {
            orderService.handlePaymentEvent(event);
        } catch (Exception e) {
            log.error("Error processing payment event: {}", event, e);
        }
    }
}