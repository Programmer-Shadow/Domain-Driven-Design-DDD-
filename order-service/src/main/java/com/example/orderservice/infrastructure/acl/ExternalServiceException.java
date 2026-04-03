package com.example.orderservice.infrastructure.acl;

/**
 * 异常：外部服务异常
 * 防腐层捕获所有外部框架异常，转换为此异常，防止外部异常污染领域层
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
