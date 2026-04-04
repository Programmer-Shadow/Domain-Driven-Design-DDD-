# DDD + 防腐层（ACL）微服务示例

这是一个展示**领域驱动设计（DDD）**和**防腐层（Anti-Corruption Layer, ACL）**最佳实践的 Java Spring Boot 微服务项目。

## 📋 项目概述

本项目包含两个微服务：

### 1. User Service（用户服务，端口 8082）
- **职责**：管理用户信息，提供用户查询 API
- **主要功能**：用户注册、用户查询
- **外部合同**：`UserResponse` DTO

### 2. Order Service（订单服务，端口 8081）
- **职责**：管理订单，通过防腐层调用 User Service 获取客户信息
- **主要功能**：订单创建、订单查询、订单支付
- **防腐层**：隔离 User Service 的外部模型，防止其污染内部领域

## 🎯 防腐层（ACL）核心设计

### 防腐层的目的
当 Order Service 需要调用 User Service 时，防腐层通过以下方式保护 Order 领域的纯净性：

1. **隔离外部模型**：外部 `ExternalUserDTO` 只存在于 `infrastructure/acl` 包，绝不外泄
2. **语义转换**：外部 `username` → 内部 `customerName`（使用 Order 域的语言）
3. **结构重组**：外部扁平地址字段 → 内部 `CustomerAddress` 值对象
4. **异常转换**：Feign 异常 → 领域化异常或 `Optional.empty()`

### ACL 的四层架构

```
防腐层的 4 个关键角色：

1. Port（端口）
   └─ UserServicePort.java
   └─ 定义：领域需要的外部能力接口，用领域语言表达
   └─ 返回：内部值对象 CustomerInfo（不是外部 DTO）

2. Adapter（适配器）
   └─ UserServiceAdapter.java
   └─ 实现 Port，协调 Client 和 Translator
   └─ 处理异常，防止外部异常污染领域层

3. Translator（翻译器）
   └─ UserTranslator.java
   └─ 核心职责：ExternalUserDTO → CustomerInfo
   └─ 字段语义映射和结构重组

4. Client（客户端）
   └─ UserServiceFeignClient.java
   └─ 纯技术通道，只负责 HTTP 调用
   └─ 返回外部 DTO，由 Translator 进一步处理
```

### 数据流向

```
Order Service 应用层
      │
      ↓
  OrderApplicationService.createOrder()
      │ 调用
      ↓
  UserServicePort.findCustomerById()（接口，不知道实现）
      │ 由 Spring 注入
      ↓
  UserServiceAdapter（实现 Port）
      │ 协调
      ├─→ UserServiceFeignClient.getUserById()
      │                  │
      │                  ↓ HTTP GET /api/users/{id}
      │         ┌─────────────────────┐
      │         │ User Service API    │
      │         │ 返回 UserResponse    │
      │         └─────────────────────┘
      │                  │
      │                  ↓ ExternalUserDTO
      │
      └─→ UserTranslator.toCustomerInfo()
                         │
                         ↓ CustomerInfo（内部值对象）

      回到应用层，继续创建订单
```

## 📁 项目结构

### User Service

```
user-service/
├── src/main/java/com/example/userservice/
│   ├── UserServiceApplication.java
│   │
│   ├── domain/                          # 领域层
│   │   ├── model/
│   │   │   ├── UserId.java             # 值对象：用户 ID
│   │   │   ├── Address.java            # 值对象：地址（record）
│   │   │   └── User.java               # 聚合根
│   │   ├── event/
│   │   │   └── UserRegisteredEvent.java # 领域事件
│   │   └── repository/
│   │       └── UserRepository.java     # 仓储接口（纯领域）
│   │
│   ├── application/                     # 应用层
│   │   ├── UserApplicationService.java
│   │   └── CreateUserCommand.java
│   │
│   ├── infrastructure/                  # 基础设施层
│   │   └── persistence/
│   │       ├── UserJpaEntity.java       # JPA 实体
│   │       ├── UserJpaRepository.java   # Spring Data JPA
│   │       ├── UserRepositoryImpl.java   # 仓储实现
│   │       └── UserPersistenceMapper.java
│   │
│   └── interfaces/                      # 接口层
│       └── rest/
│           ├── UserController.java
│           └── dto/
│               ├── CreateUserRequest.java
│               └── UserResponse.java    # 外部合同
```

### Order Service（重点：防腐层）

```
order-service/
├── src/main/java/com/example/orderservice/
│   ├── OrderServiceApplication.java
│   │
│   ├── domain/                          # 领域层（纯净）
│   │   ├── model/
│   │   │   ├── OrderId.java
│   │   │   ├── OrderStatus.java
│   │   │   ├── OrderItem.java
│   │   │   ├── Money.java
│   │   │   ├── CustomerAddress.java     # Order 域的地址表示
│   │   │   ├── CustomerInfo.java        # ★ ACL 保护的目标
│   │   │   └── Order.java               # 聚合根
│   │   ├── event/
│   │   │   └── OrderCreatedEvent.java
│   │   └── repository/
│   │       └── OrderRepository.java
│   │
│   ├── application/                     # 应用层
│   │   ├── OrderApplicationService.java # ★ 依赖 UserServicePort 接口
│   │   ├── CreateOrderCommand.java
│   │   ├── CustomerNotFoundException.java
│   │   └── OrderNotFoundException.java
│   │
│   ├── infrastructure/                  # 基础设施层
│   │   ├── persistence/
│   │   │   ├── OrderJpaEntity.java
│   │   │   ├── OrderJpaRepository.java
│   │   │   ├── OrderRepositoryImpl.java
│   │   │   └── OrderPersistenceMapper.java
│   │   │
│   │   └── acl/                         # ★ 防腐层（核心）
│   │       ├── UserServicePort.java     # 端口接口（领域定义）
│   │       ├── ExternalServiceException.java
│   │       │
│   │       └── userservice/
│   │           ├── client/
│   │           │   ├── UserServiceFeignClient.java  # Feign 通道
│   │           │   └── dto/
│   │           │       └── ExternalUserDTO.java    # 外部 DTO
│   │           │
│   │           ├── translator/
│   │           │   └── UserTranslator.java         # ★ 翻译器（核心）
│   │           │
│   │           └── adapter/
│   │               └── UserServiceAdapter.java     # ★ 适配器（门面）
│   │
│   └── interfaces/                      # 接口层
│       └── rest/
│           ├── OrderController.java
│           └── dto/
│               ├── CreateOrderRequest.java
│               └── OrderResponse.java
```

## 🏗️ DDD 分层架构

### User Service

```
interfaces/      REST API（/api/users）
    ↑ 依赖 ↓
application/     用例编排（CreateUserCommand）
    ↑ 依赖 ↓
domain/          领域核心（User, UserId, Address, UserRepository）
    ↑ 被 ↓
infrastructure/  持久化实现（UserRepositoryImpl）
```

### Order Service

```
interfaces/      REST API（/api/orders）
    ↑ 依赖 ↓
application/     用例编排（OrderApplicationService 依赖 UserServicePort）
    ↑ 依赖 ↓
domain/          领域核心（Order, CustomerInfo, OrderRepository）
    ↑ 被 ↓
infrastructure/
    ├── persistence/  JPA 持久化
    └── acl/          ★ 防腐层（隐藏外部细节）
        ├── UserServicePort（由领域依赖）
        ├── UserServiceAdapter（实现 Port）
        ├── UserTranslator（转换逻辑）
        └── UserServiceFeignClient（HTTP 通道）
```

## 🔑 防腐层三条隔离准则

### 准则 1：DTO 隔离
```
✅ 正确：ExternalUserDTO 只在 infrastructure/acl 包内使用
❌ 错误：让 ExternalUserDTO 出现在 domain 或 application 包
```

### 准则 2：语言对齐
```
外部（User Service）         Order 领域（内部）
─────────────────────────────────────────
username        ────→  customerName
address（扁平）  ────→  CustomerAddress（值对象）
```

### 准则 3：异常转换
```
FeignException  ────→  Optional.empty() 或 ExternalServiceException
（技术异常）         （领域可理解的结果）
```

## 🚀 快速开始

### 前置要求
- Java 17+
- Maven 3.8+

### 1. 构建项目

```bash
cd /home/xiekun/claudeCode

# 构建所有模块
mvn clean install

# 或分别构建
mvn -pl user-service clean install
mvn -pl order-service clean install
```

### 2. 启动 User Service

```bash
cd user-service
mvn spring-boot:run
```

服务启动后：
- REST API: http://localhost:8082/api/users
- H2 控制台: http://localhost:8082/h2-console

### 3. 启动 Order Service（新终端）

```bash
cd order-service
mvn spring-boot:run
```

服务启动后：
- REST API: http://localhost:8081/api/orders
- H2 控制台: http://localhost:8081/h2-console

## 📝 API 使用示例

### Step 1: 创建用户

```bash
curl -X POST http://localhost:8082/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "张三",
    "email": "zhang@example.com",
    "street": "中关村大街1号",
    "city": "北京",
    "province": "北京",
    "zipCode": "100080",
    "country": "中国"
  }'
```

**响应示例**：
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "张三",
  "email": "zhang@example.com",
  "street": "中关村大街1号",
  "city": "北京",
  "province": "北京",
  "zipCode": "100080",
  "country": "中国",
  "registeredAt": "2026-03-25T10:30:00"
}
```

### Step 2: 创建订单（触发防腐层）

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "P001",
        "productName": "MacBook Pro",
        "price": 9999.99,
        "quantity": 1
      },
      {
        "productId": "P002",
        "productName": "Magic Mouse",
        "price": 799.99,
        "quantity": 2
      }
    ]
  }'
```

**防腐层工作流**：
1. OrderApplicationService 调用 UserServicePort
2. UserServiceAdapter 通过 Feign Client 调用 User Service API
3. UserTranslator 将 UserResponse(username) 转换为 CustomerInfo(customerName)
4. Order 聚合根 创建成功，返回 orderId

**响应示例**：
```json
{
  "orderId": "ORD-001",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerName": "张三",
  "email": "zhang@example.com",
  "items": [
    {
      "productId": "P001",
      "productName": "MacBook Pro",
      "unitPrice": 9999.99,
      "quantity": 1,
      "subtotal": 9999.99
    },
    {
      "productId": "P002",
      "productName": "Magic Mouse",
      "unitPrice": 799.99,
      "quantity": 2,
      "subtotal": 1599.98
    }
  ],
  "status": "待支付",
  "totalAmount": 11599.97,
  "createdAt": "2026-03-25T10:35:00"
}
```

### Step 3: 查询订单

```bash
curl http://localhost:8081/api/orders/ORD-001
```

### Step 4: 支付订单

```bash
curl -X POST http://localhost:8081/api/orders/ORD-001/pay
```

## 🎓 防腐层学习重点

### 文件位置和学习顺序

1. **理解领域模型**
   - `order-service/domain/model/CustomerInfo.java` — ACL 保护的目标

2. **理解端口接口**
   - `order-service/infrastructure/acl/UserServicePort.java` — 领域定义外部能力

3. **理解翻译逻辑**
   - `order-service/infrastructure/acl/userservice/translator/UserTranslator.java` — 外部→内部转换

4. **理解适配器**
   - `order-service/infrastructure/acl/userservice/adapter/UserServiceAdapter.java` — 异常处理和协调

5. **理解应用层调用**
   - `order-service/application/OrderApplicationService.java` — 应用层如何使用 ACL

### 对比学习

| 位置 | 内容 | 用途 |
|------|------|------|
| `infrastructure/acl/userservice/client/dto/ExternalUserDTO.java` | 镜像外部 API | 接收原始数据 |
| `domain/model/CustomerInfo.java` | 内部值对象 | 订单领域使用 |
| `UserTranslator.java` | 转换逻辑 | 外部→内部 |

### 关键特性

#### 1. 值对象的不可变性
```java
// Money.java - 值对象
public record Money(BigDecimal amount) { ... }

// 操作产生新实例，不修改原对象
Money result = money.add(otherMoney);  // ✅
money.amount = newAmount;               // ❌ 编译错误
```

#### 2. 聚合根的工厂方法
```java
// Order.java
Order order = Order.create(customerInfo, items);  // ✅ 工厂方法
Order order = new Order(...);                       // ❌ 私有构造器，不允许
```

#### 3. 领域事件收集
```java
// User.java
public static User create(...) {
    User user = new User(...);
    user.domainEvents.add(new UserRegisteredEvent(...));
    return user;
}
```

#### 4. 防腐层异常转换
```java
// UserServiceAdapter.java
try {
    return Optional.of(translator.toCustomerInfo(externalUser));
} catch (FeignException.NotFound e) {
    return Optional.empty();  // 外部 404 → 领域语义：客户不存在
} catch (FeignException e) {
    throw new ExternalServiceException(...);  // 框架异常 → 领域异常
}
```

## 📚 参考资源

- **DDD 书籍**：《领域驱动设计》（Evans）
- **DDD 在 Java 中的实践**：考虑值对象、聚合根、仓储等概念
- **防腐层模式**：Hexagonal Architecture（六边形架构）的一部分

## ⚠️ 重要说明

### 这不是生产级代码
本项目是教学和学习之用，包含以下简化：
- 使用 H2 内存数据库（生产环境用 PostgreSQL、MySQL）
- 没有真正的消息队列（生产环境用 RabbitMQ、Kafka）
- 没有分布式事务处理（生产环境考虑 Saga 模式）
- Feign 没有配置熔断器（生产环境用 Hystrix、Resilience4j）

### 防腐层的成本
- 额外的转换层增加了代码量
- 需要维护 DTO 和领域对象的映射关系
- 外部模型变化需要修改翻译器

**何时值得用**：当集成的外部系统模型与本服务差异大，或外部系统可能频繁变化时，防腐层的价值就很大。

## 🤝 扩展思路

1. **添加消息队列**：使用 Spring Cloud Stream 发布领域事件
2. **添加缓存**：在 UserServiceAdapter 中添加缓存层
3. **添加熔断器**：使用 Resilience4j 保护 Feign 调用
4. **添加版本化 API**：支持多个 User Service API 版本
5. **添加测试**：单元测试、集成测试、防腐层测试

---

**作者**: Claude Code
**创建日期**: 2026-03-25
**目的**: DDD + ACL 防腐层教学示例
