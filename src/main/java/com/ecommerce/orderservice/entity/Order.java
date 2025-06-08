package com.ecommerce.orderservice.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "orders")
@Data
public class Order {
    @Id
    private String id;

    private String userId;

    @NotEmpty(message = "Order items cannot be empty")
    private List<OrderItem> items;

    private BigDecimal totalAmount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod; // ONLINE or COD

    private String status = "PENDING"; // PENDING, PLACED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, FAILED, RETURNED

    private String reason; // Reason for FAILED status

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PaymentMethod {
        ONLINE, COD
    }

    @Data
    public static class OrderItem {
        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;
    }
}