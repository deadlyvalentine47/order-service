package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.Order.OrderItem;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final TokenService tokenService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PRODUCT_SERVICE_URL = "http://localhost:8080/api/products/";
    private static final String PAYMENT_SERVICE_URL = "http://localhost:8085/api/payments/initiate";
    private static final String ORDER_EVENTS_TOPIC = "ORDER_EVENTS";

    @Retryable(value = { RestClientException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private ResponseEntity<Map> fetchProduct(Long productId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", tokenService.getServiceToken());
        return restTemplate.exchange(
                PRODUCT_SERVICE_URL + productId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
    }

    @Retryable(value = { RestClientException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void updateProductStock(Long productId, int newStock) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", tokenService.getServiceToken());
        Map<String, Object> stockUpdate = new HashMap<>();
        stockUpdate.put("stock", newStock);
        restTemplate.exchange(
                PRODUCT_SERVICE_URL + productId,
                HttpMethod.PUT,
                new HttpEntity<>(stockUpdate, headers),
                Void.class
        );
    }

    @Retryable(value = { RestClientException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void initiatePayment(String orderId, BigDecimal amount, String paymentMethod) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", tokenService.getServiceToken());
        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("orderId", orderId);
        paymentRequest.put("amount", amount);
        paymentRequest.put("paymentMethod", paymentMethod);
        restTemplate.exchange(
                PAYMENT_SERVICE_URL,
                HttpMethod.POST,
                new HttpEntity<>(paymentRequest, headers),
                Void.class
        );
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            ResponseEntity<Map> response = fetchProduct(item.getProductId());
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> product = response.getBody();
                Integer currentStock = (Integer) product.get("stock");
                updateProductStock(item.getProductId(), currentStock + item.getQuantity());
            }
        }
    }

    public Order placeOrder(Order order, String userId, String authToken) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        order.setUserId(userId);

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        if (order.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            ResponseEntity<Map> response = fetchProduct(item.getProductId());
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Product with ID " + item.getProductId() + " not found");
            }

            Map<String, Object> product = response.getBody();
            Integer stock = (Integer) product.get("stock");
            BigDecimal price = new BigDecimal(product.get("price").toString());

            if (stock < item.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product ID " + item.getProductId() + ". Available: " + stock);
            }

            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            updateProductStock(item.getProductId(), stock - item.getQuantity());
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }
        order.setTotalAmount(totalAmount);

        if (order.getPaymentMethod() == Order.PaymentMethod.COD) {
            order.setStatus("PLACED");
        }

        Order savedOrder = orderRepository.save(order);

        // Initiate payment
        initiatePayment(savedOrder.getId(), totalAmount, order.getPaymentMethod().toString());

        return savedOrder;
    }

    public Order cancelOrder(String orderId, String userId) {
        List<Order> orders = orderRepository.findByIdAndUserId(orderId, userId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order with ID " + orderId + " not found or you don't have access to it");
        }
        Order order = orders.get(0);

        if (!List.of("PENDING", "PLACED").contains(order.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus("CANCELLED");
        restoreStock(order);
        Order updatedOrder = orderRepository.save(order);

        kafkaTemplate.send(ORDER_EVENTS_TOPIC, new com.ecommerce.orderservice.event.OrderEvent() {{
            setOrderId(orderId);
            setStatus("CANCELLED");
        }});

        return updatedOrder;
    }

    public Order returnOrder(String orderId, String userId) {
        List<Order> orders = orderRepository.findByIdAndUserId(orderId, userId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order with ID " + orderId + " not found or you don't have access to it");
        }
        Order order = orders.get(0);

        if (!"DELIVERED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Cannot return order in status: " + order.getStatus());
        }

        order.setStatus("RETURNED");
        restoreStock(order);
        Order updatedOrder = orderRepository.save(order);

        kafkaTemplate.send(ORDER_EVENTS_TOPIC, new com.ecommerce.orderservice.event.OrderEvent() {{
            setOrderId(orderId);
            setStatus("RETURNED");
        }});

        return updatedOrder;
    }

    public void handlePaymentEvent(com.ecommerce.orderservice.event.PaymentEvent event) {
        log.info("Processing payment event for orderId: {}", event.getOrderId());
        List<Order> orders = orderRepository.findAllById(event.getOrderId());
        if (orders.isEmpty()) {
            log.warn("Order not found for orderId: {}", event.getOrderId());
            return;
        }
        Order order = orders.get(0);
        log.info("Current order status: {}", order.getStatus());

        if ("PAYMENT_RECEIVED".equals(event.getStatus())) {
            if ("PENDING".equals(order.getStatus())) {
                log.info("Updating order {} to PLACED", order.getId());
                order.setStatus("PLACED");
                orderRepository.save(order);
            } else {
                log.info("Order {} is not in PENDING status, current status: {}", order.getId(), order.getStatus());
            }
        } else if ("PAYMENT_FAILED".equals(event.getStatus())) {
            if (List.of("PENDING", "PLACED").contains(order.getStatus())) {
                log.info("Updating order {} to FAILED with reason: {}", order.getId(), event.getReason());
                order.setStatus("FAILED");
                order.setReason(event.getReason() != null ? event.getReason() : "payment failed");
                restoreStock(order);
                orderRepository.save(order);
            } else {
                log.info("Order {} is not in PENDING or PLACED status, current status: {}", order.getId(), order.getStatus());
            }
        }
    }

    public void handleLogisticsEvent(com.ecommerce.orderservice.event.LogisticsEvent event) {
        log.info("Processing logistics event for orderId: {}", event.getOrderId());
        List<Order> orders = orderRepository.findAllById(event.getOrderId());
        if (orders.isEmpty()) {
            log.warn("Order not found for orderId: {}", event.getOrderId());
            return;
        }
        Order order = orders.get(0);
        log.info("Current order status: {}", order.getStatus());

        String newStatus = event.getStatus();
        String currentStatus = order.getStatus();

        boolean isValidTransition = false;
        if ("SHIPPED".equals(newStatus) && "PLACED".equals(currentStatus)) {
            isValidTransition = true;
        } else if ("OUT_FOR_DELIVERY".equals(newStatus) && "SHIPPED".equals(currentStatus)) {
            isValidTransition = true;
        } else if ("DELIVERED".equals(newStatus) && "OUT_FOR_DELIVERY".equals(currentStatus)) {
            isValidTransition = true;
        }

        if (!isValidTransition) {
            log.warn("Invalid status transition for order {} from {} to {}", order.getId(), currentStatus, newStatus);
            return;
        }

        log.info("Updating order {} to {}", order.getId(), newStatus);
        order.setStatus(newStatus);
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, new com.ecommerce.orderservice.event.OrderEvent() {{
            setOrderId(order.getId());
            setStatus(newStatus);
        }});
        orderRepository.save(order);
    }

    @Scheduled(fixedRate = 60000)
    public void checkPaymentLinkExpiry() {
        List<Order> pendingOrders = orderRepository.findByStatus("PENDING");
        LocalDateTime now = LocalDateTime.now();
        for (Order order : pendingOrders) {
            if (order.getPaymentMethod() == Order.PaymentMethod.ONLINE &&
                    order.getCreatedAt().plusMinutes(5).isBefore(now)) {
                log.info("Order {} payment link expired, marking as FAILED", order.getId());
                order.setStatus("FAILED");
                order.setReason("payment link expired");
                restoreStock(order);
                orderRepository.save(order);
            }
        }
    }

    public List<Order> getOrderHistory(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        return orderRepository.findByUserId(userId);
    }

    public Order getOrderDetails(String orderId, String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }
        List<Order> orders = orderRepository.findByIdAndUserId(orderId, userId);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order with ID " + orderId + " not found or you don't have access to it");
        }
        return orders.get(0);
    }
}