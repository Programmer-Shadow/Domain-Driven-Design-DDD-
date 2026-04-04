package com.example.orderservice.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * JPA 实体：订单项
 */
@Embeddable
public class OrderItemJpaEntity {

    private String productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;

    protected OrderItemJpaEntity() {}

    public OrderItemJpaEntity(String productId, String productName, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
