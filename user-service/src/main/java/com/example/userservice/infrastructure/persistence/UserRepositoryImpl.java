package com.example.userservice.infrastructure.persistence;

import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.UserId;
import com.example.userservice.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 仓储适配器 - 适配器模式
 * 将 Spring Data JPA 接口适配为领域仓储接口
 * 领域层依赖 UserRepository 接口，不知道此类的存在
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryImpl(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        UserJpaEntity entity = mapper.toEntity(user);  // 领域对象 -> JPA 实体
        jpaRepository.save(entity);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(userId.getValue())
                .map(mapper::toDomain);                // JPA 实体 -> 领域对象
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
