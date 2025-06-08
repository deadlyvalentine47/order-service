package com.ecommerce.orderservice.event;

import lombok.Data;

@Data
public class PaymentEvent {
    private String orderId;
    private String status; // PAYMENT_RECEIVED, PAYMENT_FAILED, REFUNDED
    private String reason = ""; // For PAYMENT_FAILED, default to empty string
}