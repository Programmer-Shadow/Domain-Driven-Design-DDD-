# DDD 对象流转全路径解析

> 从「前端点击创建用户」到「创建订单完成」，每一步对象是什么、在哪变、为什么变

---

## 阅读指南

本文档跟踪一个完整的业务流程中，**数据以什么对象的形态**在各层之间流转。
每个节点标注了：

- **文件路径** — 你可以直接跳转去看
- **对象类型** — 当前数据长什么样
- **为什么要变形** — DDD 要求每层用自己的对象，原因在此

---

## 第一部分：创建用户（User Service 内部）

### 总览

```
HTTP JSON → CreateUserRequest → CreateUserCommand → User(聚合根) → UserJpaEntity → DB
                                                         ↓
                                                    UserResponse → HTTP JSON（返回给调用方）
```

---

### Step 1: 前端发 HTTP 请求

```json
POST http://localhost:8082/api/users
{
  "name": "张三",
  "email": "zhangsan@example.com",
  "street": "中关村大街1号",
  "city": "北京",
  "province": "北京市",
  "zipCode": "100080",
  "country": "China"
}
```

此时数据还是**裸 JSON**，没有任何 Java 对象。

---

### Step 2: JSON → `CreateUserRequest`

**文件**: `user-service/interfaces/rest/dto/CreateUserRequest.java`

```java
public record CreateUserRequest(
    String name, String email,
    String street, String city, String province, String zipCode, String country
) {}
```

Spring 自动把 JSON 反序列化成这个 record。

**这个对象是什么身份？**

- 它是**接口层 DTO**（Data Transfer Object）
- 职责：表示 HTTP 请求的结构，是外部世界和系统之间的合同
- 它只活在 `interfaces/rest/` 包内，绝不传入 application 层或 domain 层

**为什么不直接把它传给 Service？**
- 如果直接传，application 层就会依赖 interfaces 层的类 — 依赖方向反了
- DTO 跟着 HTTP 协议走，domain 逻辑不应该被 HTTP 的结构绑架

---

### Step 3: `CreateUserRequest` → `CreateUserCommand`

**文件**: `user-service/interfaces/rest/UserController.java` 第 36-44 行

```java
CreateUserCommand command = new CreateUserCommand(
    request.name(), request.email(),
    request.street(), request.city(), request.province(),
    request.zipCode(), request.country()
);
```

**文件**: `user-service/application/CreateUserCommand.java`

```java
public record CreateUserCommand(
    String name, String email,
    String street, String city, String province, String zipCode, String country
) {}
```

**看起来和 CreateUserRequest 一模一样？为什么要多此一举？**

这两个对象**字段相同但身份不同**：

| | CreateUserRequest | CreateUserCommand |
|---|---|---|
| 所在层 | interfaces（接口层） | application（应用层） |
| 代表什么 | HTTP 请求的结构 | 业务意图：「我要创建一个用户」 |
| 谁定义的 | HTTP API 合同 | 应用层的输入契约 |
| 变化原因 | API 版本变更（加字段、改名） | 业务需求变更 |

今天它们长一样，但未来可能不一样。比如 API 可能加一个 `source: "mobile"` 字段，但 Command 不需要这个字段 — Command 只包含业务需要的东西。

**关键理解：DDD 分层的核心不是「现在是否不同」，而是「它们因不同的原因变化」。**

---

### Step 4: `CreateUserCommand` → `User`（聚合根）+ `Address`（值对象）+ `UserId`（值对象）

**文件**: `user-service/application/UserApplicationService.java` 第 32-54 行

```java
public String createUser(CreateUserCommand command) {
    // 1. 业务规则校验
    if (userRepository.existsByEmail(command.email())) {
        throw new IllegalArgumentException("Email already exists");
    }

    // 2. 构建值对象
    Address address = new Address(
        command.street(), command.city(), command.province(),
        command.zipCode(), command.country()
    );

    // 3. 通过工厂方法创建聚合根
    User user = User.create(command.name(), command.email(), address);

    // 4. 持久化
    userRepository.save(user);

    return user.getId().getValue();
}
```

这一步发生了**关键变形**，7 个扁平的 String 变成了 3 个领域对象：

#### 对象 A: `Address`（值对象）

**文件**: `user-service/domain/model/Address.java`

```java
public record Address(
    String street, String city, String province, String zipCode, String country
) {
    public Address {
        Objects.requireNonNull(city, "City cannot be null");    // 自带校验
        Objects.requireNonNull(country, "Country cannot be null");
    }
}
```

5 个字符串被聚合成一个**有业务含义的整体**。你不能有 city 没有 country — Address 保证这种一致性。

#### 对象 B: `UserId`（值对象）

**文件**: `user-service/domain/model/UserId.java`

```java
public final class UserId {
    private final String value;

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }
}
```

为什么不直接用 `String id`？
- 防止把 userId 和 orderId 搞混（都是 String，编译器分不出来）
- `UserId.generate()` 封装了 ID 生成策略，未来换成雪花算法只改这一处

#### 对象 C: `User`（聚合根）

**文件**: `user-service/domain/model/User.java` 第 32-38 行

```java
public static User create(String name, String email, Address address) {
    validateEmail(email);
    User user = new User(UserId.generate(), name, email, address, LocalDateTime.now());
    user.domainEvents.add(new UserRegisteredEvent(user.id.getValue(), email));
    return user;
}
```

**为什么用工厂方法 `create()` 而不是 `new User()`？**

- todo构造器是 private 的，外部无法绕过工厂方法
- todo**工厂方法**里做了：校验邮箱 → 生成ID → 设置时间 → **触发领域事件**
- 这些是创建用户时的**业务不变量**，不能被跳过

此刻内存中的对象结构：

```
User {
    id: UserId { value: "550e8400-..." }
    name: "张三"
    email: "zhangsan@example.com"
    address: Address { street: "中关村大街1号", city: "北京", ... }
    createdAt: 2026-04-01T18:44:00
    domainEvents: [ UserRegisteredEvent { userId: "550e8400-...", email: "..." } ]
}
```

---

### Step 5: `User` → `UserJpaEntity`（持久化映射）

**文件**: `user-service/infrastructure/persistence/UserRepositoryImpl.java` 第 27-29 行

```java
public void save(User user) {
    UserJpaEntity entity = mapper.toEntity(user);  // 领域对象 → JPA 实体
    jpaRepository.save(entity);
}
```

**文件**: `user-service/infrastructure/persistence/UserPersistenceMapper.java` 第 18-35 行

```java
public UserJpaEntity toEntity(User user) {
    Address address = user.getAddress();
    AddressEmbeddable addressEmbeddable = new AddressEmbeddable(
        address.street(), address.city(), address.province(),
        address.zipCode(), address.country()
    );
    return new UserJpaEntity(
        user.getId().getValue(),    // UserId → String
        user.getName(),
        user.getEmail(),
        addressEmbeddable,          // Address → AddressEmbeddable
        user.getCreatedAt()
    );
}
```

**文件**: `user-service/infrastructure/persistence/UserJpaEntity.java`

```java
@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id private String id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String email;
    @Embedded private AddressEmbeddable address;
    @Column(nullable = false) private LocalDateTime createdAt;
}
```

**又是看起来差不多的对象，为什么要 User 和 UserJpaEntity 两个？**

| | User（领域模型） | UserJpaEntity（JPA 实体） |
|---|---|---|
| 所在层 | domain | infrastructure |
| 有无框架注解 | 无（纯 Java） | 有 `@Entity`, `@Table`, `@Column` |
| 有无 Setter | 无（只能通过业务方法改状态） | 有（JPA 要求） |
| 有无无参构造器 | 无 | 有 `protected UserJpaEntity() {}` （JPA 要求） |
| ID 类型 | `UserId`（值对象） | `String`（JPA 不认识 UserId） |
| 地址类型 | `Address`（record） | `AddressEmbeddable`（JPA 嵌入类型） |

**核心原因：领域模型不应该为了框架需求而妥协设计。**
如果 User 加上 `@Entity`，就必须加 Setter、无参构造器，破坏了封装性。

---

### Step 6: `User` → `UserResponse`（返回给前端 / 其他服务）

**文件**: `user-service/interfaces/rest/UserController.java` 第 77-89 行

```java
private UserResponse toUserResponse(User user) {
    return new UserResponse(
        user.getId().getValue(),
        user.getName(),         // 内部叫 name → 外部叫 username
        user.getEmail(),
        user.getAddress().street(),
        user.getAddress().city(),
        user.getAddress().province(),
        user.getAddress().zipCode(),
        user.getAddress().country(),
        user.getCreatedAt()
    );
}
```

**文件**: `user-service/interfaces/rest/dto/UserResponse.java`

```java
public record UserResponse(
    String userId,
    String username,        // ← 注意这里叫 username，不是 name
    String email,
    String street, String city, String province, String zipCode, String country,
    LocalDateTime registeredAt
) {}
```

**注意字段名的变化：**
- 内部 `name` → 外部 `username`
- 内部 `createdAt` → 外部 `registeredAt`

这是有意为之。对外的 API 合同和内部模型用不同的命名，两者可以独立演化。

---

### 创建用户完整对象流转图

```
前端 JSON
  │
  ▼
CreateUserRequest ·········· interfaces/rest/dto/     (HTTP 请求结构)
  │
  │  Controller 手动转换
  ▼
CreateUserCommand ·········· application/              (业务意图)
  │
  │  ApplicationService 编排
  ▼
┌─────────────────────────┐
│ User (聚合根)            │
│   ├─ UserId (值对象)     │·· domain/model/           (核心业务对象)
│   ├─ Address (值对象)    │
│   └─ domainEvents[]     │
└─────────────────────────┘
  │                    │
  │ Mapper.toEntity    │ Controller.toResponse
  ▼                    ▼
UserJpaEntity          UserResponse
  │                      │
  ▼                      ▼
数据库 (H2)            HTTP JSON 返回给前端/其他服务
```

**每次变形的原因：**
1. JSON → Request DTO: Spring 自动反序列化
2. Request DTO → Command: 解耦接口层和应用层（不同的变化原因）
3. Command → User + Address + UserId: 扁平数据变成有业务含义的领域对象
4. User → JpaEntity: 领域模型不被框架注解污染
5. User → Response DTO: 对外合同和内部模型独立命名

---

## 第二部分：创建订单（跨服务调用 + ACL）

### 总览

```
HTTP JSON
  │
  ▼
CreateOrderRequest ─→ CreateOrderCommand
                          │
                          │ 需要客户信息
                          ▼
                    UserServicePort.findCustomerById()
                          │
                  ┌───────┴──── ACL 防腐层 ────────┐
                  │                                 │
                  │  FeignClient → HTTP → User Service
                  │       返回 ExternalUserDTO       │
                  │              │                   │
                  │       UserTranslator             │
                  │              │                   │
                  │       CustomerInfo (内部模型)     │
                  └───────────────────────────────────┘
                          │
                          ▼
                    Order.create(customerInfo, items)
                          │
                          ▼
                    OrderJpaEntity → DB
```

---

### Step 7: 前端发创建订单请求

```json
POST http://localhost:8081/api/orders
{
  "customerId": "550e8400-...",
  "items": [{
    "productId": "PROD-001",
    "productName": "MacBook Pro",
    "price": 12999,
    "quantity": 1
  }]
}
```

---

### Step 8: JSON → `CreateOrderRequest` → `CreateOrderCommand`

**文件**: `order-service/interfaces/rest/dto/CreateOrderRequest.java`

```java
public record CreateOrderRequest(
    String customerId,
    List<OrderItemRequest> items
) {
    public record OrderItemRequest(
        String productId, String productName, double price, int quantity
    ) {}
}
```

**文件**: `order-service/interfaces/rest/OrderController.java` 第 37-47 行

```java
CreateOrderCommand command = new CreateOrderCommand(
    request.customerId(),
    request.items().stream()
        .map(item -> new CreateOrderCommand.OrderItemDto(
            item.productId(), item.productName(), item.price(), item.quantity()
        ))
        .collect(Collectors.toList())
);
```

和 User Service 同理：Request DTO → Command，解耦接口层和应用层。

---

### Step 9: 通过 ACL 获取客户信息（跨服务边界）

**文件**: `order-service/application/OrderApplicationService.java` 第 60-64 行

```java
CustomerInfo customerInfo = userServicePort
    .findCustomerById(command.customerId())
    .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
```

这行代码背后发生了很多事情。`userServicePort` 是一个接口，实际执行的是 ACL 适配器。

---

### Step 9a: ACL 第一步 — Feign 调用 User Service

**文件**: `order-service/infrastructure/acl/userservice/client/UserServiceFeignClient.java`

```java
@FeignClient(name = "user-service", url = "${acl.user-service.base-url}")
public interface UserServiceFeignClient {
    @GetMapping("/api/users/{userId}")
    ExternalUserDTO getUserById(@PathVariable("userId") String userId);
}
```

Feign 发出 HTTP 请求 `GET http://localhost:8082/api/users/550e8400-...`

User Service 返回的 JSON 被反序列化为 `ExternalUserDTO`。

---

### Step 9b: ACL 第二步 — 接收为 `ExternalUserDTO`

**文件**: `order-service/infrastructure/acl/userservice/client/dto/ExternalUserDTO.java`

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalUserDTO(
    String userId,
    String username,        // ← User Service 的字段名
    String email,
    String street, String city, String province, String zipCode, String country,
    LocalDateTime registeredAt
) {}
```

**这个对象和 User Service 的 `UserResponse` 结构一模一样，为什么 Order Service 要自己定义一个？**

因为 Order Service **不应该依赖 User Service 的 jar 包**。如果共用 `UserResponse`：
- User Service 改 `UserResponse`，Order Service 必须同步升级
- 两个服务的发布周期被绑定 — 微服务独立部署的优势没了

`ExternalUserDTO` 是 Order Service **自己对外部 API 的理解**，加了 `@JsonIgnoreProperties(ignoreUnknown = true)` — User Service 加新字段不会导致反序列化崩溃。

---

### Step 9c: ACL 第三步 — `ExternalUserDTO` → `CustomerInfo`（翻译）

**文件**: `order-service/infrastructure/acl/userservice/translator/UserTranslator.java`

```java
public CustomerInfo toCustomerInfo(ExternalUserDTO externalDTO) {
    // 语义对齐：username → customerName
    String customerName = externalDTO.username();

    // 结构重组：5 个扁平字段 → CustomerAddress 值对象
    CustomerAddress shippingAddress = buildCustomerAddress(externalDTO);

    // 选择性投影：registeredAt 被丢弃（订单不需要）
    return new CustomerInfo(
        externalDTO.userId(),
        customerName,
        externalDTO.email(),
        shippingAddress
    );
}
```

**翻译前后的对比：**

```
ExternalUserDTO (外部世界)          CustomerInfo (订单域)
─────────────────────────          ─────────────────────
userId: "550e8400-..."        →    customerId: "550e8400-..."     (保持)
username: "张三"              →    customerName: "张三"           (重命名)
email: "zhangsan@..."         →    email: "zhangsan@..."          (保持)
street: "中关村大街1号"        ┐
city: "北京"                  │→   defaultAddress: CustomerAddress {
province: "北京市"            │       street, city, province,     (结构重组)
zipCode: "100080"             │       zipCode, country
country: "China"              ┘    }
registeredAt: "2026-04-01..." →    (丢弃，订单不关心)
```

---

### Step 9d: ACL 外层 — `UserServiceAdapter` 协调一切

**文件**: `order-service/infrastructure/acl/userservice/adapter/UserServiceAdapter.java`

```java
@Component
public class UserServiceAdapter implements UserServicePort {

    public Optional<CustomerInfo> findCustomerById(String userId) {
        try {
            ExternalUserDTO externalUser = feignClient.getUserById(userId);  // 9a
            CustomerInfo customerInfo = translator.toCustomerInfo(externalUser);  // 9c
            return Optional.of(customerInfo);
        } catch (FeignException.NotFound e) {
            return Optional.empty();       // 404 → 领域语义：客户不存在
        } catch (FeignException e) {
            throw new ExternalServiceException("...", e);  // 框架异常 → 领域异常
        }
    }
}
```

**适配器的三件事：**
1. 调用 FeignClient 拿到外部数据
2. 调用 Translator 翻译为内部模型
3. 把框架异常翻译为领域异常（`FeignException` 永远不会泄漏到应用层）

---

### Step 10: `CustomerInfo` + `CreateOrderCommand` → `Order`（聚合根）

**文件**: `order-service/application/OrderApplicationService.java` 第 67-78 行

```java
// 构建订单项值对象
List<OrderItem> items = command.items().stream()
    .map(item -> new OrderItem(
        item.productId(),
        item.productName(),
        new Money(item.price()),     // double → Money 值对象
        item.quantity()
    ))
    .collect(Collectors.toList());

// 创建订单聚合根
Order order = Order.create(customerInfo, items);
```

**这一步的变形：**
- `double price` → `Money` 值对象（防止金额计算精度问题）
- `OrderItemDto` → `OrderItem` record（带业务校验：数量必须 > 0）

**文件**: `order-service/domain/model/Order.java` 第 29-47 行

```java
public static Order create(CustomerInfo customerInfo, List<OrderItem> items) {
    Objects.requireNonNull(customerInfo, "CustomerInfo is required");
    if (items == null || items.isEmpty()) {
        throw new IllegalArgumentException("Order must have at least one item");
    }

    OrderId orderId = OrderId.generate();
    Money total = calculateTotal(items);    // 遍历所有 item 求和
    Order order = new Order(orderId, customerInfo, new ArrayList<>(items),
                            OrderStatus.PENDING, total, LocalDateTime.now());

    order.domainEvents.add(new OrderCreatedEvent(
        orderId.getValue(), customerInfo.customerId(), total.amount()
    ));
    return order;
}
```

此刻 Order 聚合根的完整结构：

```
Order {
    id: OrderId { value: "7f3a9c..." }
    customerInfo: CustomerInfo {          ← 来自 ACL 翻译，不是 ExternalUserDTO
        customerId: "550e8400-..."
        customerName: "张三"              ← 不是 username
        email: "zhangsan@..."
        defaultAddress: CustomerAddress { street, city, ... }
    }
    items: [
        OrderItem {
            productId: "PROD-001"
            productName: "MacBook Pro"
            unitPrice: Money { amount: 12999.0 }
            quantity: 1
        }
    ]
    status: PENDING
    totalAmount: Money { amount: 12999.0 }
    createdAt: 2026-04-01T18:44:00
    domainEvents: [ OrderCreatedEvent { ... } ]
}
```

**注意：Order 里没有任何 User Service 的痕迹。** 它只知道 `CustomerInfo`，不知道 `ExternalUserDTO`、`FeignClient`、`UserResponse` 的存在。

---

### Step 11: `Order` → `OrderJpaEntity` → DB

和 User Service 的 Step 5 同理，通过 `OrderPersistenceMapper` 把领域对象转为 JPA 实体存库。

---

### Step 12: `Order` → `OrderResponse` → 返回前端

**文件**: `order-service/interfaces/rest/OrderController.java` 第 91-110 行

```java
private OrderResponse toOrderResponse(Order order) {
    return new OrderResponse(
        order.getId().getValue(),
        order.getCustomerInfo().customerId(),
        order.getCustomerInfo().customerName(),   // ← 这里是 customerName
        order.getCustomerInfo().email(),
        order.getItems().stream()
            .map(item -> new OrderResponse.OrderItemResponse(
                item.productId(), item.productName(),
                item.unitPrice().amount(),           // Money → BigDecimal
                item.quantity(),
                item.subtotal().amount()             // Money → BigDecimal
            ))
            .collect(Collectors.toList()),
        order.getStatus().getDescription(),
        order.getTotalAmount().amount(),
        order.getCreatedAt()
    );
}
```

---

## 第三部分：全路径对象一览表

### 创建用户（User Service 内部，5 次变形）

| 步骤 | 对象 | 所在层 | 文件 | 变形原因 |
|------|------|--------|------|----------|
| 1 | HTTP JSON | 网络 | — | 传输格式 |
| 2 | `CreateUserRequest` | interfaces | `dto/CreateUserRequest.java` | HTTP 请求合同 |
| 3 | `CreateUserCommand` | application | `CreateUserCommand.java` | 业务意图，解耦接口层 |
| 4 | `User` + `Address` + `UserId` | domain | `model/User.java` 等 | 核心业务对象，带校验和事件 |
| 5a | `UserJpaEntity` + `AddressEmbeddable` | infrastructure | `persistence/UserJpaEntity.java` | 持久化，不污染领域模型 |
| 5b | `UserResponse` | interfaces | `dto/UserResponse.java` | 对外 API 合同（username, registeredAt） |

### 创建订单（跨服务，10+ 次变形）

| 步骤 | 对象 | 所在层 | 服务 | 文件 | 变形原因 |
|------|------|--------|------|------|----------|
| 7 | HTTP JSON | 网络 | — | — | 传输格式 |
| 8a | `CreateOrderRequest` | interfaces | Order | `dto/CreateOrderRequest.java` | HTTP 请求合同 |
| 8b | `CreateOrderCommand` | application | Order | `CreateOrderCommand.java` | 业务意图 |
| 9-FeignHTTP | HTTP JSON | 网络 | Order→User | — | 跨服务调用 |
| 9-UserSide | `User` → `UserResponse` | interfaces | User | `dto/UserResponse.java` | User Service 的对外合同 |
| 9a | HTTP JSON | 网络 | User→Order | — | 返回传输 |
| 9b | `ExternalUserDTO` | infra/acl | Order | `client/dto/ExternalUserDTO.java` | 镜像外部 API（不共享 jar） |
| 9c | `CustomerInfo` + `CustomerAddress` | domain | Order | `model/CustomerInfo.java` | **ACL 翻译产物**，订单域的语言 |
| 10 | `Order` + `OrderItem` + `Money` | domain | Order | `model/Order.java` | 核心业务对象 |
| 11 | `OrderJpaEntity` | infrastructure | Order | `persistence/OrderJpaEntity.java` | 持久化 |
| 12 | `OrderResponse` | interfaces | Order | `dto/OrderResponse.java` | 对外 API 合同 |

---

## 第四部分：为什么这么多对象？一句话总结

**DDD 的每次对象变形，都是为了让每一层只知道自己该知道的事情。**

- **interfaces 层的 DTO** — 只关心 HTTP 长什么样
- **application 层的 Command** — 只关心业务意图是什么
- **domain 层的聚合根和值对象** — 只关心业务规则和不变量
- **infrastructure 层的 JPA Entity** — 只关心怎么存到数据库
- **ACL 层的 ExternalDTO + Translator** — 只关心外部世界的模型和内部模型的差异

如果你觉得"太多对象了"，对比一下不分层的代价：

```java
// 不分层的写法：一个类干所有事
@Entity                          // 被 JPA 绑架
@Table(name = "orders")
public class Order {
    @Id private String id;
    private String username;     // 被 User Service 的命名绑架
    @Column private double price;// 用 double 算钱，精度炸了
    // ...JPA 要求的 setter 暴露了所有字段，谁都能乱改状态
}
```

这种写法在小项目能跑，但一旦 User Service 改了字段名、数据库换了、业务规则复杂了 — 改一处，处处崩。

**DDD 的分层 = 用编译期的对象边界，换取运行期的变更隔离。**
