package com.example.orderservice.application.port;

import com.example.orderservice.domain.model.CustomerInfo;
import java.util.Optional;

/**
 * 出站端口（Driven Port / Secondary Port）
 *
 * 为什么放在 application 层而不是 infrastructure 层：
 * - 端口接口代表的是"应用层的需求"，不是"基础设施的能力"
 * - 依赖倒置原则（DIP）：高层模块定义接口，低层模块实现接口
 * - application 层定义 "我需要什么"（获取客户信息）
 * - infrastructure 层实现 "怎么拿到"（Feign + ACL 翻译）
 *
 * 六边形架构中的位置：
 *   Domain Layer    ← 纯业务逻辑
 *   Application Layer ← 用例编排 + 端口定义（本接口在这里）
 *   Infrastructure Layer ← 适配器实现（UserServiceAdapter 实现本接口）
 *
 * 依赖方向：Infrastructure → Application → Domain（始终向内指）
 */
public interface UserServicePort {

    /**
     * 根据用户 ID 查询客户信息快照
     *
     * 注意返回的是 CustomerInfo（领域值对象），不是 ExternalUserDTO：
     * - 调用者（OrderApplicationService）永远不会接触外部模型
     * - 翻译工作由 infrastructure 层的 UserTranslator 完成
     *
     * @param userId 用户 ID
     * @return 客户信息值对象，包含 Order 业务需要的所有信息
     */
    Optional<CustomerInfo> findCustomerById(String userId);
}
