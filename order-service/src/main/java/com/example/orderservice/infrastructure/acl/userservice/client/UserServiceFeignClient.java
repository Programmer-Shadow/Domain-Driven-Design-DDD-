package com.example.orderservice.infrastructure.acl.userservice.client;

import com.example.orderservice.infrastructure.acl.userservice.client.dto.ExternalUserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign 客户端：User Service HTTP 通道
 *
 * 职责：
 * - 只负责 HTTP 通信，不涉及任何业务语义
 * - 返回原始的外部 DTO（ExternalUserDTO）
 * - Translator 和 Adapter 会进一步处理此 DTO
 */
@FeignClient(
    name = "user-service",
    url = "${acl.user-service.base-url}"
)
public interface UserServiceFeignClient {

    @GetMapping("/api/users/{userId}")
    ExternalUserDTO getUserById(@PathVariable("userId") String userId);
}
