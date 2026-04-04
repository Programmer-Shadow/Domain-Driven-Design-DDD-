package com.example.orderservice.infrastructure.acl.userservice.adapter;

import com.example.orderservice.domain.model.CustomerInfo;
import com.example.orderservice.infrastructure.acl.ExternalServiceException;
import com.example.orderservice.application.port.UserServicePort;
import com.example.orderservice.infrastructure.acl.userservice.client.UserServiceFeignClient;
import com.example.orderservice.infrastructure.acl.userservice.client.dto.ExternalUserDTO;
import com.example.orderservice.infrastructure.acl.userservice.translator.UserTranslator;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 防腐层适配器（Anti-Corruption Layer Adapter）
 *
 * 这是 ACL 的对外门面，实现了 UserServicePort 接口。
 *
 * 职责：
 * 1. 协调 FeignClient（HTTP 调用）和 Translator（模型转换）
 * 2. 处理外部服务的异常（网络错误、404、5xx），转换为领域可理解的结果
 * 3. 防止外部服务的异常模型污染领域层
 * 4. 可在此处添加熔断、重试、缓存等横切关注点
 *
 * 架构意义：
 * - 这个类是唯一知道 Feign Client 和 ExternalUserDTO 存在的类
 * - 上游的 domain 层、application 层完全看不见外部的技术细节
 * - 如果外部 API 变化，只需修改此类和 Translator，不影响业务逻辑
 */
@Component
public class UserServiceAdapter implements UserServicePort {

    private static final Logger log = LoggerFactory.getLogger(UserServiceAdapter.class);

    private final UserServiceFeignClient feignClient;
    private final UserTranslator translator;

    public UserServiceAdapter(UserServiceFeignClient feignClient, UserTranslator translator) {
        this.feignClient = feignClient;
        this.translator = translator;
    }

    @Override
    public Optional<CustomerInfo> findCustomerById(String userId) {
        try {
            // 1. 调用外部 User Service，获取原始 ExternalUserDTO
            ExternalUserDTO externalUser = feignClient.getUserById(userId);

            // 2. 通过翻译器将外部模型转换为内部领域模型
            //    这是防腐层的核心：外部 DTO 永远不会穿透到 domain 或 application 层
            CustomerInfo customerInfo = translator.toCustomerInfo(externalUser);

            return Optional.of(customerInfo);

        } catch (FeignException.NotFound e) {
            // 外部服务返回 404 -> 翻译为领域语义：客户不存在
            log.warn("User not found in UserService for userId: {}", userId);
            return Optional.empty();

        } catch (FeignException e) {
            // 外部服务其他错误（5xx, 网络超时等）
            // -> 翻译为领域可理解的异常
            // 注意：不抛出 FeignException，防止外部框架异常渗透到领域层
            log.error("Failed to call UserService for userId: {}. HTTP Status: {}. Error: {}",
                    userId, e.status(), e.getMessage(), e);

            throw new ExternalServiceException(
                "Unable to retrieve customer information from User Service. Please try again later.",
                e  // 保留原始异常供日志追踪，但对外暴露领域化异常
            );

        } catch (Exception e) {
            // 其他未预期的异常（数据格式错误、null 指针等）
            log.error("Unexpected error while retrieving customer information for userId: {}", userId, e);
            throw new ExternalServiceException(
                "An unexpected error occurred while retrieving customer information.",
                e
            );
        }
    }
}
