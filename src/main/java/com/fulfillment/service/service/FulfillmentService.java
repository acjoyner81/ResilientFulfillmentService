package com.fulfillment.service.service;

import com.fulfillment.service.dto.OrderRequest;
import com.fulfillment.service.dto.OrderResponse;
import com.fulfillment.service.model.Order;
import com.fulfillment.service.model.OrderStatus;
import com.fulfillment.service.model.ProductCache;
import com.fulfillment.service.repository.jpa.OrderRepository;
import com.fulfillment.service.repository.redis.ProductRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentService {

    private final OrderRepository orderRepository;
    private final ProductRedisRepository productRedisRepository;
    private final InventoryClientService inventoryClientService;
    private final Counter ordersFulfilledCounter;
    private final Counter ordersFallbackCounter;
    private final Timer orderProcessingTimer;

    @Transactional
    public OrderResponse processOrder(OrderRequest request) {
        return orderProcessingTimer.record(() -> {
            String orderNum = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Processing order request: orderNumber={}, productId={}", orderNum, request.getProductId());

            // Step 1: Redis Cache Check / Update for Product Meta
            try {
                productRedisRepository.findById(request.getProductId())
                        .orElseGet(() -> {
                            log.info("Redis cache miss for productId={}. Populating cache...", request.getProductId());
                            ProductCache newCache = new ProductCache(
                                    request.getProductId(),
                                    "Product " + request.getProductId(),
                                    request.getPricePerUnit(),
                                    100
                            );
                            return productRedisRepository.save(newCache);
                        });
            } catch (Exception e) {
                log.warn("Redis unavailable for caching, proceeding with relational persistence: {}", e.getMessage());
            }

            BigDecimal total = request.getPricePerUnit().multiply(BigDecimal.valueOf(request.getQuantity()));

            // Step 2: Downstream Resilience Call (Bulkhead & Circuit Breaker)
            boolean inventoryReserved = inventoryClientService.verifyAndReserveInventory(request.getProductId(), request.getQuantity());

            OrderStatus finalStatus = inventoryReserved ? OrderStatus.FULFILLED : OrderStatus.FALLBACK_PROCESSING;
            String responseMessage = inventoryReserved ? 
                    "Order successfully verified and fulfilled." : 
                    "Downstream inventory service unavailable/degraded. Order queued in fallback state.";

            // Increment Observability Counters for Dynatrace & Prometheus
            if (inventoryReserved) {
                ordersFulfilledCounter.increment();
            } else {
                ordersFallbackCounter.increment();
            }

            // Step 3: Persist Order in PostgreSQL / Relational DB
            Order order = Order.builder()
                    .orderNumber(orderNum)
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .totalPrice(total)
                    .status(finalStatus)
                    .customerEmail(request.getCustomerEmail())
                    .build();

            Order savedOrder = orderRepository.save(order);
            log.info("Order saved to database: id={}, status={}", savedOrder.getId(), savedOrder.getStatus());

            return OrderResponse.builder()
                    .id(savedOrder.getId())
                    .orderNumber(savedOrder.getOrderNumber())
                    .productId(savedOrder.getProductId())
                    .quantity(savedOrder.getQuantity())
                    .totalPrice(savedOrder.getTotalPrice())
                    .status(savedOrder.getStatus())
                    .message(responseMessage)
                    .createdAt(savedOrder.getCreatedAt())
                    .build();
        });
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        return mapToResponse(order);
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .message("Fetched successfully")
                .createdAt(order.getCreatedAt())
                .build();
    }
}
