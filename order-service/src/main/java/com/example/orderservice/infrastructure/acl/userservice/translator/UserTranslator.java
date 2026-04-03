package com.example.orderservice.infrastructure.acl.userservice.translator;

import com.example.orderservice.domain.model.CustomerAddress;
import com.example.orderservice.domain.model.CustomerInfo;
import com.example.orderservice.infrastructure.acl.userservice.client.dto.ExternalUserDTO;
import org.springframework.stereotype.Component;

/**
 * 翻译器（Translator / Assembler）
 *
 * 防腐层的核心职责：
 * 1. 将外部世界的模型（ExternalUserDTO）翻译为本领域的概念（CustomerInfo）
 * 2. 字段映射：外部 username -> 内部 customerName（语义对齐）
 * 3. 结构重组：外部扁平地址字段 -> 内部 CustomerAddress 值对象
 * 4. 数据清洗：处理 null、格式转换等
 * 5. 隔离变化：外部 DTO 结构变化只需修改此类，不影响领域层
 *
 * 这个翻译过程保证了：
 * - Order 领域不会被 User Service 的模型污染
 * - 两个界限上下文可以独立演化
 * - 模型变化的影响被限制在防腐层内
 */
@Component
public class UserTranslator {

    /**
     * 将外部用户 DTO 转换为 Order 领域的客户信息快照
     *
     * @param externalDTO 来自 User Service 的原始响应
     * @return Order 领域内部使用的 CustomerInfo 值对象
     * @throws IllegalArgumentException 如果 DTO 为 null 或数据不完整
     */
    public CustomerInfo toCustomerInfo(ExternalUserDTO externalDTO) {
        if (externalDTO == null) {
            throw new IllegalArgumentException("Cannot translate null ExternalUserDTO");
        }

        // 关键翻译点1：字段语义对齐
        // 外部叫 "username"，内部叫 "customerName"（使用 Order 领域的语言）
        String customerName = externalDTO.username();
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("External user username is missing or empty");
        }

        // 关键翻译点2：结构重组
        // 外部是扁平的地址字段，内部是 CustomerAddress 值对象
        CustomerAddress shippingAddress = buildCustomerAddress(externalDTO);

        // 关键翻译点3：只提取 Order 业务需要的信息，不全盘引入
        // 注意：registeredAt 在 Order 域中不需要，所以不转换
        return new CustomerInfo(
            externalDTO.userId(),
            customerName,
            externalDTO.email(),
            shippingAddress
        );
    }

    /**
     * 从外部 DTO 构建 Order 域的 CustomerAddress
     * 此方法处理 null 和默认值
     */
    private CustomerAddress buildCustomerAddress(ExternalUserDTO externalDTO) {
        return new CustomerAddress(
            externalDTO.street() != null ? externalDTO.street() : "",
            externalDTO.city() != null ? externalDTO.city() : "Unknown",
            externalDTO.province() != null ? externalDTO.province() : "",
            externalDTO.zipCode() != null ? externalDTO.zipCode() : "",
            externalDTO.country() != null ? externalDTO.country() : "China"
        );
    }
}
