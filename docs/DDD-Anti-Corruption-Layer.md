# DDD 防腐层（Anti-Corruption Layer）学习指南

> 基于 user-service 和 order-service 两个微服务的实际代码

---

## 一、什么是防腐层（ACL）

防腐层是 DDD 中的一种**边界保护模式**，用于在两个限界上下文（Bounded Context）之间建立翻译层，
防止外部模型污染本域的领域模型。

### 核心思想

```
没有 ACL 的世界（危险）：
  Order Service 直接使用 UserResponse DTO
  → 外部字段名 "username" 渗透到订单业务代码
  → User Service 改了 API 结构，Order 的业务逻辑也被迫改
  → 两个服务紧耦合

有 ACL 的世界（安全）：
  Order Service 只知道 CustomerInfo（自己的值对象）
  → 外部怎么变，只需要改 ACL 翻译层
  → 业务逻辑零改动
```

### 一句话定义

> ACL = **在系统边界处设立翻译层，将外部世界的语言翻译成本域的语言。**

---

## 二、为什么需要防腐层

### 2.1 微服务间的模型差异

User Service 和 Order Service 是两个**独立的限界上下文**，它们对同一个概念有不同的理解：

| 概念 | User Service（用户域） | Order Service（订单域） |
|------|----------------------|----------------------|
| 人 | `User`（聚合根，包含完整用户生命周期） | `CustomerInfo`（值对象，仅是下单时的快照） |
| 名字 | `name`（内部）/ `username`（API） | `customerName`（订单域的通用语言） |
| 地址 | `Address`（扁平字段） | `CustomerAddress`（结构化值对象） |
| 注册时间 | `registeredAt`（重要） | 不需要（订单不关心用户何时注册） |

**如果不用 ACL，Order Service 直接用 UserResponse：**
- 代码中到处是 `userResponse.username()` — 这不是订单域的语言
- 地址字段散落各处 — 没有结构化，容易出错
- User Service 加了新字段 — Order 的反序列化可能崩
- 字段改名 — Order 的业务逻辑要跟着改

### 2.2 保护领域模型的纯净性

DDD 的核心原则：**领域层不依赖任何外部框架和外部服务**。

```java
// ❌ 被污染的 Order 域 — 直接依赖 User Service 的 DTO
public class Order {
    private UserResponse customer;  // 外部 DTO 侵入了领域模型！
}

// ✅ 被 ACL 保护的 Order 域 — 只使用自己的值对象
public class Order {
    private CustomerInfo customerInfo;  // 订单域自己的概念
}
```

---

## 三、本项目的 ACL 架构

### 3.1 完整数据流

```
┌─────────────────────────────────────────────────────────────┐
│                    Order Service                            │
│                                                             │
│  ┌─────────────┐    ┌──────────────────┐                   │
│  │ Application  │    │ Domain Layer     │                   │
│  │ Layer        │    │                  │                   │
│  │              │    │  Order (聚合根)   │                   │
│  │ OrderApp     │───>│  CustomerInfo    │                   │
│  │ Service      │    │  CustomerAddress │                   │
│  │              │    │                  │                   │
│  │      │       │    └──────────────────┘                   │
│  │      │ 依赖                                              │
│  │      ▼                                                   │
│  │ UserServicePort ◄─── 端口接口（定义在 application 层）     │
│  └──────┬──────────────────────────────────────────────────┘ │
│         │ 实现                                               │
│  ┌──────▼──────────────────────────────────────────────────┐ │
│  │ Infrastructure / ACL 层                                 │ │
│  │                                                         │ │
│  │  UserServiceAdapter (实现 UserServicePort)               │ │
│  │      │                                                  │ │
│  │      ├──→ UserServiceFeignClient  ──HTTP──→ User Service │ │
│  │      │         返回 ExternalUserDTO                      │ │
│  │      │                                                  │ │
│  │      └──→ UserTranslator                                │ │
│  │              ExternalUserDTO → CustomerInfo              │ │
│  │              (外部模型)        (内部模型)                  │ │
│  │                                                         │ │
│  │  ExternalServiceException (领域化异常)                    │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 ACL 各组件的职责

| 组件 | 位置 | 职责 | 文件 |
|------|------|------|------|
| **UserServicePort** | `application/port/` | 定义"需要什么"（端口接口） | `UserServicePort.java` |
| **UserServiceAdapter** | `infrastructure/acl/` | 协调调用和翻译（适配器） | `UserServiceAdapter.java` |
| **UserServiceFeignClient** | `infrastructure/acl/` | HTTP 通信（传输层） | `UserServiceFeignClient.java` |
| **ExternalUserDTO** | `infrastructure/acl/` | 镜像外部 API 结构（从不外泄） | `ExternalUserDTO.java` |
| **UserTranslator** | `infrastructure/acl/` | 模型翻译（ACL 核心） | `UserTranslator.java` |
| **CustomerInfo** | `domain/model/` | 内部领域值对象（翻译目标） | `CustomerInfo.java` |
| **CustomerAddress** | `domain/model/` | 内部地址值对象 | `CustomerAddress.java` |
| **ExternalServiceException** | `infrastructure/acl/` | 异常翻译（防止框架异常外泄） | `ExternalServiceException.java` |

---

## 四、ACL 的五大翻译机制（结合代码）

### 4.1 字段语义对齐

外部 API 和内部领域用不同的词表达同一个概念：

```java
// UserTranslator.java 核心翻译
public CustomerInfo toCustomerInfo(ExternalUserDTO externalDTO) {
    // 外部叫 "username"，内部叫 "customerName"
    // 这不是简单的重命名，而是语义对齐：
    // User 域的 "username" 是用户的登录名/显示名
    // Order 域的 "customerName" 是下单客户的名称
    String customerName = externalDTO.username();
    // ...
}
```

**为什么不直接用 username？**
- 订单域的通用语言中没有 "username" 这个词
- 如果 User Service 把 username 改为 displayName，不应该影响订单的业务逻辑
- 领域语言的一致性比技术便利更重要

### 4.2 结构重组

外部 API 返回扁平字段，内部需要结构化值对象：

```java
// 外部 API 返回（ExternalUserDTO）：
{
    "street": "中关村大街1号",
    "city": "北京",
    "province": "北京市",
    "zipCode": "100080",
    "country": "China"
}

// 内部领域模型（CustomerAddress 值对象）：
private CustomerAddress buildCustomerAddress(ExternalUserDTO externalDTO) {
    return new CustomerAddress(
        externalDTO.street() != null ? externalDTO.street() : "",
        externalDTO.city() != null ? externalDTO.city() : "Unknown",
        externalDTO.province() != null ? externalDTO.province() : "",
        externalDTO.zipCode() != null ? externalDTO.zipCode() : "",
        externalDTO.country() != null ? externalDTO.country() : "China"
    );
}
```

**结构化的好处：**
- `CustomerAddress` 作为值对象有自己的校验逻辑
- 地址相关的操作封装在一个对象中
- 比散落的5个 String 字段更安全

### 4.3 选择性投影

只提取本域需要的信息：

```java
// User Service 返回 9 个字段
record ExternalUserDTO(
    String userId,
    String username,
    String email,
    String street, city, province, zipCode, country,
    LocalDateTime registeredAt    // ← Order 域不需要这个
) {}

// Order 域只取 4 个概念
record CustomerInfo(
    String customerId,
    String customerName,
    String email,
    CustomerAddress defaultAddress  // 5个字段被结构化为1个值对象
) {}
```

`registeredAt` 对 User 域很重要，但 Order 域不关心用户何时注册。ACL 丢弃了这个字段。

### 4.4 异常翻译

外部框架的异常不能穿透到领域层：

```java
// UserServiceAdapter.java
@Override
public Optional<CustomerInfo> findCustomerById(String userId) {
    try {
        ExternalUserDTO externalUser = feignClient.getUserById(userId);
        return Optional.of(translator.toCustomerInfo(externalUser));

    } catch (FeignException.NotFound e) {
        // HTTP 404 → 领域语义："客户不存在"
        return Optional.empty();

    } catch (FeignException e) {
        // Feign 框架异常 → 领域化异常
        // 如果不翻译，OrderApplicationService 就要 import FeignException
        // 那就意味着 application 层知道了 Feign 的存在 — 违反分层原则
        throw new ExternalServiceException("...", e);
    }
}
```

### 4.5 防御性设计

```java
// ExternalUserDTO 的 @JsonIgnoreProperties(ignoreUnknown = true)
// User Service 加了新字段？不会导致 Order Service 反序列化失败

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalUserDTO(...) {}
```

---

## 五、依赖方向 — 最容易犯的错

### 5.1 正确的依赖方向

```
Domain Layer     ← 不依赖任何东西
    ↑
Application Layer ← 只依赖 Domain
    ↑
Infrastructure Layer ← 依赖 Application 和 Domain
    ↑
Interface Layer  ← 依赖 Application
```

### 5.2 本项目修复的问题

**修复前（错误）：**
```
application/OrderApplicationService
    └── import infrastructure.acl.UserServicePort  ← ❌ application 依赖了 infrastructure
```

**修复后（正确）：**
```
application/OrderApplicationService
    └── import application.port.UserServicePort    ← ✅ 同层依赖

infrastructure/acl/UserServiceAdapter
    └── implements application.port.UserServicePort ← ✅ 低层实现高层接口（依赖倒置）
```

### 5.3 依赖倒置原则（DIP）图解

```
┌──────────────────────────────────────┐
│      Application Layer               │
│                                      │
│  OrderApplicationService             │
│       │                              │
│       ▼                              │
│  UserServicePort (接口)  ◄───────────┼─── 接口定义在高层
│                                      │
└──────────────────────────────────────┘
                 ▲
                 │ implements（依赖方向向上）
                 │
┌──────────────────────────────────────┐
│      Infrastructure Layer            │
│                                      │
│  UserServiceAdapter                  │
│  UserServiceFeignClient              │
│  UserTranslator                      │
│  ExternalUserDTO                     │
│                                      │
└──────────────────────────────────────┘
```

---

## 六、微服务间传递对象的 DDD 规范

### 6.1 绝对不要做的事

```java
// ❌ 1. 直接在领域层使用外部 DTO
public class Order {
    private ExternalUserDTO customer;  // 外部 DTO 入侵领域
}

// ❌ 2. Application 层直接调用 Feign Client
@Service
public class OrderApplicationService {
    private final UserServiceFeignClient feignClient;  // 直接耦合基础设施

    public void createOrder(...) {
        ExternalUserDTO user = feignClient.getUserById(id);  // 外部模型泄漏
        order.setCustomerName(user.username());  // 外部字段名入侵
    }
}

// ❌ 3. 共享 DTO jar 包
// 把 UserResponse 打成 jar 包让 Order Service 依赖
// → 两个服务的发布周期被绑定
// → User Service 改 DTO，Order Service 必须同步升级

// ❌ 4. 不做翻译，直接用
CustomerInfo info = new CustomerInfo(
    dto.getUserId(),
    dto.getUsername(),  // 没翻译，直接把外部名字传进来
    dto.getEmail(),
    // 扁平字段直接传，没有结构化
);
```

### 6.2 正确的做法（本项目已实现）

```
步骤 1: 定义内部领域模型
  → CustomerInfo, CustomerAddress（在 domain/model/ 中）
  → 使用本域的通用语言命名

步骤 2: 定义端口接口
  → UserServicePort（在 application/port/ 中）
  → 方法签名只使用领域类型，不出现外部类型

步骤 3: 创建外部 DTO 镜像
  → ExternalUserDTO（在 infrastructure/acl/ 中）
  → 与外部 API 1:1 对应，加 @JsonIgnoreProperties

步骤 4: 实现翻译器
  → UserTranslator（在 infrastructure/acl/ 中）
  → 字段映射 + 结构重组 + 选择性投影 + null 处理

步骤 5: 实现适配器
  → UserServiceAdapter implements UserServicePort
  → 协调 FeignClient + Translator + 异常翻译

步骤 6: 应用层只依赖端口
  → OrderApplicationService 依赖 UserServicePort
  → 对 Feign、HTTP、外部 DTO 一无所知
```

### 6.3 完整调用链（创建订单时）

```
1. Controller 收到请求
   POST /api/orders { customerId: "usr-123", items: [...] }

2. Controller → CreateOrderCommand（命令对象）

3. OrderApplicationService.createOrder(command)
   │
   ├── 4. userServicePort.findCustomerById("usr-123")
   │       │
   │       ├── 5. [ACL] FeignClient → HTTP GET /api/users/usr-123
   │       │       → User Service 返回 JSON → ExternalUserDTO
   │       │
   │       ├── 6. [ACL] UserTranslator.toCustomerInfo(externalDTO)
   │       │       username → customerName
   │       │       扁平地址 → CustomerAddress
   │       │       丢弃 registeredAt
   │       │       → 返回 CustomerInfo（领域值对象）
   │       │
   │       └── 7. [ACL] UserServiceAdapter 返回 Optional<CustomerInfo>
   │
   ├── 8. 构建 OrderItem 列表
   │
   ├── 9. Order.create(customerInfo, items)
   │       → 聚合根工厂方法
   │       → 触发 OrderCreatedEvent
   │
   └── 10. orderRepository.save(order)
           → 通过持久化层的 Mapper 转为 JPA Entity
           → 保存到数据库
```

---

## 七、ACL 包结构规范

```
order-service/
├── application/
│   ├── port/
│   │   └── UserServicePort.java          ← 端口接口（高层定义）
│   ├── OrderApplicationService.java       ← 用例编排（依赖端口）
│   └── CreateOrderCommand.java
│
├── domain/
│   └── model/
│       ├── Order.java                     ← 聚合根
│       ├── CustomerInfo.java              ← 内部模型（ACL 翻译目标）
│       └── CustomerAddress.java           ← 内部值对象
│
└── infrastructure/
    └── acl/
        ├── ExternalServiceException.java   ← 领域化异常
        └── userservice/                    ← 按外部服务分子包
            ├── client/
            │   ├── UserServiceFeignClient.java   ← HTTP 通信
            │   └── dto/
            │       └── ExternalUserDTO.java      ← 外部模型镜像（不外泄）
            ├── adapter/
            │   └── UserServiceAdapter.java       ← 适配器（实现端口）
            └── translator/
                └── UserTranslator.java           ← 模型翻译器
```

**关键规则：**
- `ExternalUserDTO` 永远不出现在 `acl` 包之外
- `CustomerInfo` 永远不出现在 `acl` 包之内（它是翻译的产出，不是输入）
- 新增外部服务依赖时，在 `acl/` 下创建新的子包（如 `acl/paymentservice/`）

---

## 八、如果没有 ACL 会怎样（反面案例）

假设 User Service 做了以下变更：

| 变更 | 没有 ACL 的影响 | 有 ACL 的影响 |
|------|----------------|--------------|
| `username` 改名为 `displayName` | Order 所有用到 username 的业务代码要改 | 只改 `ExternalUserDTO` + `UserTranslator` |
| 新增 `phoneNumber` 字段 | 可能导致反序列化异常 | `@JsonIgnoreProperties` 自动忽略 |
| 地址拆成 `primaryAddress` + `secondaryAddress` | Order 域的地址逻辑全部重写 | 只改 `UserTranslator.buildCustomerAddress()` |
| API 从 REST 改为 gRPC | Order 整个调用链重写 | 只改 `FeignClient` → gRPC Client，其余不变 |
| User Service 宕机 | 异常直接传播，可能不可控 | `UserServiceAdapter` 统一捕获，返回领域化异常 |

---

## 九、扩展阅读：相关 DDD 概念

### 9.1 限界上下文（Bounded Context）
- User Service 和 Order Service 各自是一个限界上下文
- 每个上下文有自己的通用语言（Ubiquitous Language）
- ACL 是上下文之间的"翻译官"

### 9.2 上下文映射（Context Mapping）
DDD 定义了多种上下文间的关系模式：

| 模式 | 说明 | 本项目 |
|------|------|--------|
| **防腐层（ACL）** | 下游保护自己不被上游污染 | Order Service 用 ACL 保护自己 |
| **开放主机服务（OHS）** | 上游提供标准化 API | User Service 的 REST API |
| **发布语言（PL）** | 上下文间的共享数据格式 | UserResponse DTO（JSON） |
| **客户-供应商** | 上下游有明确依赖关系 | Order(客户) ← User(供应商) |
| **共享内核** | 两个上下文共享部分模型 | 本项目没有使用（ACL更解耦） |

### 9.3 值对象 vs 实体
- `CustomerInfo` 是**值对象**（不是实体），因为它是客户信息的**快照**
- 下单时刻的客户信息被"冻结"在订单中
- 即使用户后来改名了，订单中记录的还是下单时的名字
- 这是 DDD 中"事件时间快照"的典型应用

### 9.4 六边形架构（Ports & Adapters）
```
        ┌──────────────────────────┐
        │      Application Core     │
  ──────►  Port ──→ Domain Logic    │
  Input │        ←── Port  ─────────►──── Output
  Adapter│                          │    Adapter
        └──────────────────────────┘

  UserServicePort = Output Port（出站端口）
  UserServiceAdapter = Output Adapter（出站适配器）
  OrderController = Input Adapter（入站适配器）
```

---

## 十、Checklist：判断你的 ACL 是否合格

- [ ] 领域层（domain/）有没有 import 任何外部 DTO？**不应该有**
- [ ] Application 层有没有 import Feign/RestTemplate/HttpClient？**不应该有**
- [ ] 端口接口（Port）的方法签名是否只使用领域类型？**应该是**
- [ ] 外部 DTO 是否有 `@JsonIgnoreProperties(ignoreUnknown = true)`？**应该有**
- [ ] 外部 DTO 是否只存在于 `infrastructure/acl/` 包内？**应该是**
- [ ] 异常是否被翻译为领域化异常？**应该是**
- [ ] 翻译器是否处理了 null 和默认值？**应该是**
- [ ] Port 接口是否定义在 application 层或 domain 层？**应该是**
- [ ] 新增外部服务时，能否不改 domain 层？**应该能**

本项目在修复 `UserServicePort` 位置后，全部满足 ✅

---

## 参考资料

- Eric Evans,《领域驱动设计》第14章 — 保持模型完整性
- Vaughn Vernon,《实现领域驱动设计》第13章 — 集成限界上下文
- Alistair Cockburn, Hexagonal Architecture (Ports and Adapters)
- Martin Fowler, "BoundedContext" — https://martinfowler.com/bliki/BoundedContext.html
