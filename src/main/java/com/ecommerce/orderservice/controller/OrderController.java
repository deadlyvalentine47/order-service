package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody Order order) {
        JwtAuthenticationToken authToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String userId = authToken.getToken().getClaim("sub").toString();
        String token = "Bearer " + authToken.getToken().getTokenValue();
        Order createdOrder = orderService.placeOrder(order, userId, token);
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable String id) {
        JwtAuthenticationToken authToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String userId = authToken.getToken().getClaim("sub").toString();
        return ResponseEntity.ok(orderService.cancelOrder(id, userId));
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<Order> returnOrder(@PathVariable String id) {
        JwtAuthenticationToken authToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String userId = authToken.getToken().getClaim("sub").toString();
        return ResponseEntity.ok(orderService.returnOrder(id, userId));
    }

    @GetMapping
    public ResponseEntity<List<Order>> getOrderHistory() {
        JwtAuthenticationToken authToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String userId = authToken.getToken().getClaim("sub").toString();
        return ResponseEntity.ok(orderService.getOrderHistory(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderDetails(@PathVariable String id) {
        JwtAuthenticationToken authToken = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String userId = authToken.getToken().getClaim("sub").toString();
        return ResponseEntity.ok(orderService.getOrderDetails(id, userId));
    }
}