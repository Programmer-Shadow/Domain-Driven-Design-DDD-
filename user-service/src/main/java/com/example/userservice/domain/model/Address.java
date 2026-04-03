package com.example.userservice.domain.model;

import java.util.Objects;

/**
 * 值对象：地址
 * 不可变、通过所有属性判断相等性的值对象
 */
public record Address(
    String street,
    String city,
    String province,
    String zipCode,
    String country
) {
    public Address {
        Objects.requireNonNull(city, "City cannot be null");
        Objects.requireNonNull(country, "Country cannot be null");
    }

    /**
     * 值对象的变更产生新实例，不修改原对象（不可变性）
     */
    public Address withCity(String newCity) {
        return new Address(street, newCity, province, zipCode, country);
    }

    public Address withCountry(String newCountry) {
        return new Address(street, city, province, zipCode, newCountry);
    }
}
