package com.fulfillment.service.service;

import com.fulfillment.service.model.Order;
import com.fulfillment.service.model.OrderStatus;
import com.fulfillment.service.model.ProductCache;
import com.fulfillment.service.repository.jpa.OrderRepository;
import com.fulfillment.service.repository.redis.ProductRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRedisRepository productRedisRepository;

    @Transactional
    public Order createOrder(String orderNumber, String productId, int quantity, BigDecimal price, String email) {
        log.info("OrderService creating order: orderNumber={}, productId={}", orderNumber, productId);

        // Redis cache check
        try {
            productRedisRepository.findById(productId).orElseGet(() -> {
                ProductCache newCache = new ProductCache(productId, "Product " + productId, price, 100);
                return productRedisRepository.save(newCache);
            });
        } catch (Exception e) {
            log.warn("Redis caching bypassed: {}", e.getMessage());
        }

        // Relational DB persistence
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .productId(productId)
                .quantity(quantity)
                .totalPrice(price.multiply(BigDecimal.valueOf(quantity)))
                .status(OrderStatus.FULFILLED)
                .customerEmail(email)
                .build();

        return orderRepository.save(order);
    }

    public List<Order> getOrders() {
        return orderRepository.findAll();
    }
}
