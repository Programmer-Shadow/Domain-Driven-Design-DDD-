package com.example.userservice.domain.repository;

import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.UserId;
import java.util.Optional;

/**
 * 仓储接口 - 纯领域接口
 * 不依赖任何框架（JPA、Spring 等）
 * 由基础设施层的实现类提供具体实现
 */
public interface UserRepository {

    void save(User user);

    Optional<User> findById(UserId userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
