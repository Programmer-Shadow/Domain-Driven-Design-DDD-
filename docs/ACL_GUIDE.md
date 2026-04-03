# 防腐层（Anti-Corruption Layer, ACL）详细指南

本文档深入讲解防腐层的设计、实现和最佳实践。

## 📖 什么是防腐层？

**防腐层** 是一个隔离边界，防止外部系统的模型和设计污染本服务的领域模型。

### 类比
```
╔══════════════════════════════════════╗
║     Order Service（你的领域）          ║
║  ┌──────────────────────────────────┐ ║
║  │  Order, CustomerInfo, ...        │ ║
║  │  （纯净、专注于订单业务）         │ ║
║  └──────────────────────────────────┘ ║
║           ↑                            ║
║           │（受保护）                  ║
║  ┌────────┴────────┐                  ║
║  │   防腐层 (ACL)   │  ← 隔离边界     ║
║  │  UserServicePort│                  ║
║  │ UserTranslator  │                  ║
║  │ UserServiceAdapter                 ║
║  └────────┬────────┘                  ║
║           │                            ║
╚═══════════╪════════════════════════════╝
            │ （危险区）
            ↓
      User Service
   外部 API + 模型
    （可能频繁变化）
```

## 🏗️ ACL 的四层架构

### 第 1 层：端口接口（Port）

```java
// UserServicePort.java
// 位置：infrastructure/acl/UserServicePort.java
// 虽然在 infrastructure 包，但代表 domain 的需求

public interface UserServicePort {
    Optional<CustomerInfo> findCustomerById(String userId);
}
```

**关键特点**：
- 定义在 infrastructure 包，但用 **领域语言** 定义
- 返回 **内部领域对象**（CustomerInfo），不是外部 DTO
- domain 和 application 层只依赖此接口
- 对外部实现细节（Feign、HTTP）一无所知

**为什么这样设计**：
```
❌ 错误做法：
domain/model/Order 依赖 UserServiceFeignClient（Feign 框架）
→ 领域层与框架耦合，难以测试，容易变化

✅ 正确做法：
domain 依赖 UserServicePort（接口）
infrastructure 实现 UserServicePort（使用 Feign）
→ 领域层独立，framework 变化不影响领域，易于测试
```

### 第 2 层：客户端（Client）

```java
// UserServiceFeignClient.java
// 位置：infrastructure/acl/userservice/client/

@FeignClient(name = "user-service", url = "${acl.user-service.base-url}")
public interface UserServiceFeignClient {
    @GetMapping("/api/users/{userId}")
    ExternalUserDTO getUserById(@PathVariable("userId") String userId);
}
```

**职责**：
- 纯技术通道，只负责 HTTP 通信
- 返回原始的外部 DTO（ExternalUserDTO）
- 无任何业务语义

**特点**：
- 完全隔离在 `infrastructure/acl` 包内
- 其他包看不见 Feign、HTTP、外部 DTO
- 如果外部 API 改为 gRPC，只需修改这一个类

### 第 3 层：翻译器（Translator）

```java
// UserTranslator.java
// 位置：infrastructure/acl/userservice/translator/

@Component
public class UserTranslator {

    public CustomerInfo toCustomerInfo(ExternalUserDTO externalDTO) {
        // 翻译点 1：字段语义对齐
        String customerName = externalDTO.username();  // 外部：username → 内部：customerName

        // 翻译点 2：结构重组
        CustomerAddress shippingAddress = new CustomerAddress(
            externalDTO.street(),
            externalDTO.city(),
            // 扁平的地址字段 → 值对象
        );

        // 翻译点 3：选择性映射
        return new CustomerInfo(
            externalDTO.userId(),
            customerName,
            externalDTO.email(),
            shippingAddress
            // 注意：registeredAt 不映射，Order 业务不需要
        );
    }
}
```

**核心职责**：
1. **字段语义对齐**：外部术语 → 本领域术语
   ```
   外部：username    →   内部：customerName
   外部：city        →   内部：city
   外部：registeredAt →  （不映射，Order 不关心）
   ```

2. **结构重组**：扁平结构 → 值对象
   ```
   外部（扁平）：
   {
     street: "...",
     city: "...",
     province: "...",
     zipCode: "...",
     country: "..."
   }

   内部（值对象）：
   new CustomerAddress(street, city, province, zipCode, country)
   ```

3. **数据清洗**：处理 null、格式转换
   ```java
   String city = externalDTO.city() != null ?
       externalDTO.city() : "Unknown";
   ```

4. **选择性映射**：只映射本领域需要的字段
   ```
   不映射：registeredAt（User 业务，Order 不需要）
   ```

**为什么需要翻译器**：
- 外部 DTO 结构可能不适合本领域使用
- 外部字段名可能与本领域语言不一致
- 外部可能有很多本领域用不到的字段
- 如果外部 API 新增字段，翻译器可以选择忽略

### 第 4 层：适配器（Adapter）

```java
// UserServiceAdapter.java
// 位置：infrastructure/acl/userservice/adapter/

@Component
public class UserServiceAdapter implements UserServicePort {

    private final UserServiceFeignClient feignClient;
    private final UserTranslator translator;

    @Override
    public Optional<CustomerInfo> findCustomerById(String userId) {
        try {
            // 1. 调用 Client（HTTP）获取外部 DTO
            ExternalUserDTO externalUser = feignClient.getUserById(userId);

            // 2. 通过 Translator 转换为内部对象
            CustomerInfo customerInfo = translator.toCustomerInfo(externalUser);

            return Optional.of(customerInfo);

        } catch (FeignException.NotFound e) {
            // 3. 异常转换：404 → Optional.empty()
            // 外部返回用户不存在 → 内部理解为"查询结果为空"
            return Optional.empty();

        } catch (FeignException e) {
            // 4. 异常转换：技术异常 → 领域异常
            // 不能让 FeignException 泄露到 domain 层
            throw new ExternalServiceException(
                "Unable to retrieve customer information.",
                e
            );
        }
    }
}
```

**核心职责**：
1. **协调**：组织 Client 和 Translator 的调用顺序
2. **异常处理**：转换框架异常为领域可理解的结果
3. **实现 Port**：是 Port 接口的唯一实现

**关键异常转换**：

| 外部异常 | 内部转换 | 原因 |
|---------|---------|------|
| FeignException.NotFound（404） | Optional.empty() | 用户不存在 → 查询结果为空 |
| FeignException.FeignClientException（5xx） | ExternalServiceException | 外部服务故障 → 领域可理解的异常 |
| 网络超时 | ExternalServiceException | 无法获取数据 → 领域可理解的异常 |
| 数据格式错误 | ExternalServiceException | 无法转换 → 领域可理解的异常 |

---

## 🔄 完整的防腐层数据流

```
User 发起创建订单请求
│
├─→ OrderController.createOrder(CreateOrderRequest)
│
├─→ OrderApplicationService.createOrder(CreateOrderCommand)
│   │
│   └─→ userServicePort.findCustomerById(customerId)  ← 调用端口接口
│       │
│       └─→ UserServiceAdapter.findCustomerById()     ← Spring 注入实现
│           │
│           ├─→ UserServiceFeignClient.getUserById()  ← HTTP 调用
│           │   │
│           │   └─ 返回 ExternalUserDTO (来自 User Service)
│           │      {
│           │        userId: "abc",
│           │        username: "张三",    ← 外部字段名
│           │        email: "...",
│           │        street: "...",
│           │        city: "北京",
│           │        ...
│           │      }
│           │
│           ├─→ UserTranslator.toCustomerInfo(ExternalUserDTO)
│           │   │
│           │   └─ 返回 CustomerInfo (内部领域对象)
│           │      {
│           │        customerId: "abc",
│           │        customerName: "张三",  ← 内部字段名（翻译后）
│           │        email: "...",
│           │        defaultAddress: CustomerAddress(...)  ← 结构重组
│           │      }
│           │
│           └─ 返回 Optional<CustomerInfo>
│
├─→ 回到 OrderApplicationService
│   Order.create(customerInfo, items)  ← 使用内部对象创建订单
│
├─→ 持久化、发布事件
│
└─→ OrderController 返回 OrderResponse 给客户端
```

## 🛡️ 防腐层的隔离边界

### 隔离规则

#### 规则 1：外部 DTO 永不出境

```
✅ 正确：
infrastructure/acl/userservice/client/dto/ExternalUserDTO.java

❌ 错误：
domain/model/ExternalUserDTO.java         ← domain 层！
application/ExternalUserDTO.java          ← application 层！
interfaces/rest/dto/ExternalUserDTO.java  ← REST 返回给客户端！
```

**检查方法**：
```bash
# 检查是否有不该出现 ExternalUserDTO 的地方
grep -r "ExternalUserDTO" domain/
grep -r "ExternalUserDTO" application/
grep -r "ExternalUserDTO" interfaces/

# 应该只在这个包出现：
grep -r "ExternalUserDTO" infrastructure/acl/userservice/client/dto/
```

#### 规则 2：Feign 框架代码永不出境

```
✅ 正确：
infrastructure/acl/userservice/client/UserServiceFeignClient.java

❌ 错误：
application/OrderApplicationService.java {
    @Autowired UserServiceFeignClient feignClient;  ← Feign 在应用层！
}

domain/Order.java {
    private UserServiceFeignClient client;  ← Feign 在领域层！
}
```

**检查方法**：
```bash
# 应该只在 Feign Client 出现 @FeignClient
grep -r "@FeignClient" application/     # 不应该有结果
grep -r "@FeignClient" domain/          # 不应该有结果
grep -r "FeignClient" application/      # 不应该有结果
grep -r "FeignClient" domain/           # 不应该有结果
```

#### 规则 3：Port 接口只在应用层和适配器出现

```
✅ 正确：
application/OrderApplicationService.java {
    private final UserServicePort userServicePort;  ← 应用层依赖接口
}

infrastructure/acl/userservice/adapter/UserServiceAdapter.java {
    public class UserServiceAdapter implements UserServicePort  ← 适配器实现接口
}

❌ 错误：
domain/Order.java {
    private UserServicePort port;  ← 领域层！
}

interfaces/rest/OrderController.java {
    @Autowired UserServicePort port;  ← 应该通过 application service
}
```

## 💡 实践建议

### 1. 如何测试防腐层

```java
@Test
public void testUserTranslator_PropertyMapping() {
    // 外部 DTO
    ExternalUserDTO externalDTO = new ExternalUserDTO(
        "user-123",
        "张三",           // 外部：username
        "zhang@example.com",
        "中关村大街",
        "北京",
        "北京",
        "100080",
        "中国",
        LocalDateTime.now()
    );

    // 转换
    CustomerInfo customerInfo = translator.toCustomerInfo(externalDTO);

    // 断言：验证翻译是否正确
    assertThat(customerInfo.customerId()).isEqualTo("user-123");
    assertThat(customerInfo.customerName()).isEqualTo("张三");  // username → customerName
    assertThat(customerInfo.defaultAddress().city()).isEqualTo("北京");
}

@Test
public void testUserServiceAdapter_NotFound() {
    // Mock Feign Client：返回 404
    when(feignClient.getUserById("unknown"))
        .thenThrow(new FeignException.NotFound("User not found", null, null));

    // 调用 Adapter
    Optional<CustomerInfo> result = adapter.findCustomerById("unknown");

    // 断言：应该返回 empty，而不是抛出异常
    assertThat(result).isEmpty();
}
```

### 2. 如何修改防腐层

**场景：User Service API 新增字段**
```
新 API 响应：
{
  userId: "...",
  username: "...",
  email: "...",
  ...
  vipLevel: "GOLD"  ← 新增字段
}
```

**修改步骤**：
```java
// Step 1：ExternalUserDTO 添加新字段
@JsonIgnoreProperties(ignoreUnknown=true)  // 这行很关键！
public record ExternalUserDTO(
    String userId,
    String username,
    String email,
    // ...
    String vipLevel        // ← 新增
) {}

// Step 2：Translator 可选择映射或忽略
public class UserTranslator {
    public CustomerInfo toCustomerInfo(ExternalUserDTO externalDTO) {
        // 选项 A：映射到内部（如果 Order 业务需要）
        // return new CustomerInfo(
        //     ...,
        //     externalDTO.vipLevel()
        // );

        // 选项 B：忽略（如果 Order 业务不需要）
        return new CustomerInfo(...);  // 不映射 vipLevel
    }
}

// Step 3：domain 层和 application 层无需改动！
// Order, CustomerInfo, OrderApplicationService 都不需要修改
```

**关键点**：
- `@JsonIgnoreProperties(ignoreUnknown=true)` 防止陌生字段导致反序列化错误
- Translator 可以选择性映射，Order 业务不需要的字段可以忽略
- 这样外部 API 的变化被完全隔离在防腐层

### 3. 如何处理外部 API 变更

**场景 1：User Service 改名 username → name**
```
修改：ExternalUserDTO
修改：UserTranslator.toCustomerInfo()
无需改动：domain, application, interfaces
```

**场景 2：User Service 分离为两个 API**
```
修改：UserServiceFeignClient（新增一个 method）
修改：UserServiceAdapter（协调两个 API 调用）
修改：UserTranslator（如果有新的数据格式）
无需改动：domain, application, interfaces（或只需改 Port 接口的签名，最多）
```

**场景 3：User Service 从 HTTP 改为 gRPC**
```
修改：UserServiceFeignClient → UserServiceGrpcClient
修改：UserServiceAdapter（调用 gRPC）
修改：ExternalUserDTO → ExternalUserProto（Protocol Buffer）
修改：UserTranslator（可能需要）
无需改动：domain, application, interfaces
```

### 4. 何时值得用防腐层

#### 强烈推荐使用
- ✅ 集成遗留系统（模型陈旧、API 设计不好）
- ✅ 集成第三方服务（API 不是为你设计的）
- ✅ 集成频繁变化的服务（需要隔离变化)
- ✅ 跨界限上下文（两个系统的模型差异大）

#### 可能没必要
- ❌ 集成自己完全控制的服务（虽然好的架构是好的实践）
- ❌ API 模型与本领域一致（用翻译器的成本超过收益）

## 📊 防腐层的成本与收益

### 成本
- 💵 额外的代码量（Port, Translator, Adapter）
- 💵 维护 DTO 和领域对象的映射关系
- 💵 学习曲线（理解 Port、Translator 等模式）

### 收益
- 🎁 领域模型纯净（不被外部污染）
- 🎁 易于测试（领域层独立于框架）
- 🎁 易于演化（外部变化不影响领域）
- 🎁 易于理解（外部服务的复杂性被隐藏）

### 成本-收益判断
```
防腐层值得用：
外部系统变化频率高 + 模型差异大 + 项目周期长

防腐层可能过度设计：
内部系统 + 模型一致 + 短期项目
```

---

## 🎯 总结：防腐层的三条黄金法则

### 法则 1：隔离
> 外部模型永不进入 domain 和 application 层

### 法则 2：转换
> Translator 负责字段名、结构、数据的转换

### 法则 3：吸收
> Adapter 吸收所有外部异常，转换为领域可理解的结果

遵循这三条法则，你的领域模型就能保持纯净，系统就能健康演化。
