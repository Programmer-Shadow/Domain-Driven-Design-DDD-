package com.example.userservice.application;

/**
 * 命令对象：创建用户
 * 应用层接收来自接口层的请求，转换为此命令对象
 */
public record CreateUserCommand(
    String name,
    String email,
    String street,
    String city,
    String province,
    String zipCode,
    String country
) {}
