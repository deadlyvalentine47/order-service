package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.entity.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByUserId(String userId); // Fetch all orders for a specific user (for order history).
    List<Order> findByIdAndUserId(String id, String userId); // Fetch a specific order for a user (for order details).
    List<Order> findAllById(String id);
    List<Order> findByStatus(String status);
}