package com.ecommerce.orderservice.event;

import lombok.Data;

@Data
public class LogisticsEvent {
    private String orderId;
    private String status; // SHIPPED, OUT_FOR_DELIVERY, DELIVERED
}