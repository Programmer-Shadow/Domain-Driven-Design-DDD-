package com.example.userservice.interfaces.rest.dto;

/**
 * 请求 DTO：创建用户
 * 这是接口层的输入合同
 */
public record CreateUserRequest(
    String name,
    String email,
    String street,
    String city,
    String province,
    String zipCode,
    String country
) {}
