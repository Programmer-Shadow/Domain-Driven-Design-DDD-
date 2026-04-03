package com.example.userservice.interfaces.rest;

import com.example.userservice.application.CreateUserCommand;
import com.example.userservice.application.UserApplicationService;
import com.example.userservice.domain.model.User;
import com.example.userservice.interfaces.rest.dto.CreateUserRequest;
import com.example.userservice.interfaces.rest.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST 控制器：用户接口
 * 职责：
 * 1. 处理 HTTP 请求
 * 2. 转换 DTO <-> 应用层命令对象
 * 3. 返回响应 DTO（外部合同）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    /**
     * POST /api/users - 创建用户
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
            request.name(),
            request.email(),
            request.street(),
            request.city(),
            request.province(),
            request.zipCode(),
            request.country()
        );

        String userId = userApplicationService.createUser(command);

        // 查询刚创建的用户
        User user = userApplicationService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User creation failed"));

        UserResponse response = toUserResponse(user);
        return ResponseEntity
                .created(URI.create("/api/users/" + userId))
                .body(response);
    }

    /**
     * GET /api/users/{userId} - 获取用户信息
     * 这个接口会被 Order Service 的 Feign Client 调用
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("userId") String userId) {
        User user = userApplicationService.getUserById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * 将领域对象转换为响应 DTO（外部合同）
     */
    private UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId().getValue(),
            user.getName(),                             // 外部叫 "username"
            user.getEmail(),
            user.getAddress().street(),
            user.getAddress().city(),
            user.getAddress().province(),
            user.getAddress().zipCode(),
            user.getAddress().country(),
            user.getCreatedAt()
        );
    }
}
