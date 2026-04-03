package com.example.userservice.infrastructure.persistence;

import com.example.userservice.domain.model.Address;
import com.example.userservice.domain.model.User;
import com.example.userservice.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * 持久化映射器
 * 负责将领域模型 User 与 JPA 实体 UserJpaEntity 互相转换
 */
@Component
public class UserPersistenceMapper {

    /**
     * 将领域对象转换为 JPA 实体（用于保存）
     */
    public UserJpaEntity toEntity(User user) {
        Address address = user.getAddress();
        AddressEmbeddable addressEmbeddable = new AddressEmbeddable(
            address.street(),
            address.city(),
            address.province(),
            address.zipCode(),
            address.country()
        );

        return new UserJpaEntity(
            user.getId().getValue(),
            user.getName(),
            user.getEmail(),
            addressEmbeddable,
            user.getCreatedAt()
        );
    }

    /**
     * 将 JPA 实体转换为领域对象（用于查询）
     */
    public User toDomain(UserJpaEntity entity) {
        AddressEmbeddable addressEmbeddable = entity.getAddress();
        Address address = new Address(
            addressEmbeddable.getStreet(),
            addressEmbeddable.getCity(),
            addressEmbeddable.getProvince(),
            addressEmbeddable.getZipCode(),
            addressEmbeddable.getCountry()
        );

        return User.reconstitute(
            UserId.of(entity.getId()),
            entity.getName(),
            entity.getEmail(),
            address,
            entity.getCreatedAt()
        );
    }
}
