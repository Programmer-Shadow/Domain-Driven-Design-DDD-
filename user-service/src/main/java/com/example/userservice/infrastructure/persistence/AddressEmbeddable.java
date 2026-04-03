package com.example.userservice.infrastructure.persistence;

import jakarta.persistence.Embeddable;

/**
 * JPA 嵌入值对象
 * 将 Address 领域值对象映射为 JPA 可以持久化的嵌入类
 */
@Embeddable
public class AddressEmbeddable {
    private String street;
    private String city;
    private String province;
    private String zipCode;
    private String country;

    // JPA 需要无参构造器
    protected AddressEmbeddable() {}

    public AddressEmbeddable(String street, String city, String province, String zipCode, String country) {
        this.street = street;
        this.city = city;
        this.province = province;
        this.zipCode = zipCode;
        this.country = country;
    }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
