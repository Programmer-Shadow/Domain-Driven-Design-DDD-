package com.example.orderservice.interfaces.rest;

import com.example.orderservice.application.CreateOrderCommand;
import com.example.orderservice.application.OrderApplicationService;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.interfaces.rest.dto.CreateOrderRequest;
import com.example.orderservice.interfaces.rest.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * REST 控制器：订单接口
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * POST /api/orders - 创建订单
     * 这个接口演示了防腐层的工作原理：
     * 内部调用 ACL（UserServicePort）获取客户信息，
     * 而客户端完全感受不到有任何外部服务调用
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand(
            request.customerId(),
            request.items().stream()
                    .map(item -> new CreateOrderCommand.OrderItemDto(
                        item.productId(),
                        item.productName(),
                        item.price(),
                        item.quantity()
                    ))
                    .collect(Collectors.toList())
        );

        String orderId = orderApplicationService.createOrder(command);

        // 查询刚创建的订单
        Order order = orderApplicationService.getOrderById(orderId)
                .orElseThrow(() -> new RuntimeException("Order creation failed"));

        OrderResponse response = toOrderResponse(order);
        return ResponseEntity
                .created(URI.create("/api/orders/" + orderId))
                .body(response);
    }

    /**
     * GET /api/orders/{orderId} - 获取订单详情
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("orderId") String orderId) {
        Order order = orderApplicationService.getOrderById(orderId)
                .orElse(null);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toOrderResponse(order));
    }

    /**
     * POST /api/orders/{orderId}/pay - 支付订单
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderResponse> payOrder(@PathVariable("orderId") String orderId) {
        orderApplicationService.payOrder(orderId);
        Order order = orderApplicationService.getOrderById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        return ResponseEntity.ok(toOrderResponse(order));
    }

    /**
     * 将领域对象转换为响应 DTO
     */
    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
            order.getId().getValue(),
            order.getCustomerInfo().customerId(),
            order.getCustomerInfo().customerName(),
            order.getCustomerInfo().email(),
            order.getItems().stream()
                    .map(item -> new OrderResponse.OrderItemResponse(
                        item.productId(),
                        item.productName(),
                        item.unitPrice().amount(),
                        item.quantity(),
                        item.subtotal().amount()
                    ))
                    .collect(Collectors.toList()),
            order.getStatus().getDescription(),
            order.getTotalAmount().amount(),
            order.getCreatedAt()
        );
    }
}
