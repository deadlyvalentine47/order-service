package com.ecommerce.orderservice.event;

import lombok.Data;

@Data
public class OrderEvent {
    private String orderId;
    private String status; // CANCELLED, RETURNED
}