package com.example.userservice.application;

import com.example.userservice.domain.model.Address;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.UserId;
import com.example.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 应用层：用户应用服务
 * 职责：
 * 1. 编排领域对象和领域服务
 * 2. 事务管理
 * 3. 处理跨界限上下文的协调
 */
@Service
@Transactional
public class UserApplicationService {

    private final UserRepository userRepository;

    public UserApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 用例：创建用户
     */
    public String createUser(CreateUserCommand command) {
        // 检查邮箱唯一性
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("Email already exists: " + command.email());
        }

        // 构建地址值对象
        Address address = new Address(
            command.street(),
            command.city(),
            command.province(),
            command.zipCode(),
            command.country()
        );

        // 创建用户聚合根（工厂方法）
        User user = User.create(command.name(), command.email(), address);

        // 持久化
        userRepository.save(user);

        // 返回用户 ID
        return user.getId().getValue();
    }

    /**
     * 查询：获取用户信息（供其他服务调用）
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(String userId) {
        return userRepository.findById(UserId.of(userId));
    }

    /**
     * 查询：获取用户信息（供其他服务调用）
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
