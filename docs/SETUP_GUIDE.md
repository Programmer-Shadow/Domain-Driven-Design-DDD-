# 项目设置和运行指南

## 📋 环境要求

- **Java**: 17 或更高版本
- **Maven**: 3.8.0 或更高版本
- **操作系统**: Windows, macOS, Linux 均可

## ✅ 验证环境

### 检查 Java 版本
```bash
java -version
# 输出应该显示 Java 17 或更高

# 输出示例：
# openjdk version "17.0.5" 2022-10-18
# OpenJDK Runtime Environment (build 17.0.5+8-post-Ubuntu-0ubuntu120.04)
```

### 检查 Maven 版本
```bash
mvn -version
# 输出应该显示 Maven 3.8.0 或更高

# 输出示例：
# Apache Maven 3.8.1 (05682f26a340528abc6de3cdff4645fa32755e7c)
# Maven home: /usr/share/maven
# Java version: 17.0.5, vendor: Private Build
```

### 检查 Git（可选，用于版本控制）
```bash
git --version
```

## 🚀 快速启动（3 步）

### 第 1 步：构建项目

```bash
cd /home/xiekun/claudeCode

# 清理并构建所有模块
mvn clean install

# 或跳过测试更快（测试会在下面单独运行）
mvn clean install -DskipTests
```

**预期输出**：
```
[INFO] Reactor Summary for ddd-acl-demo 1.0.0-SNAPSHOT:
[INFO] ddd-acl-demo ...................................... SUCCESS [  1.234 s]
[INFO] user-service ...................................... SUCCESS [ 15.234 s]
[INFO] order-service ..................................... SUCCESS [ 12.456 s]
[INFO] BUILD SUCCESS
```

### 第 2 步：启动 User Service

**新开一个终端窗口**（保持此窗口运行）：

```bash
cd /home/xiekun/claudeCode/user-service
mvn spring-boot:run
```

**预期输出**（最后几行）：
```
2026-03-25 10:30:00.000  INFO 12345 --- [           main] c.e.u.UserServiceApplication            : Started UserServiceApplication in 5.234 seconds (JVM running for 5.890)
```

**验证 User Service 启动成功**：
```bash
# 在另一个终端运行（不要关闭上一个终端）
curl http://localhost:8082/api/users/test
# 应该返回 404（用户不存在），而不是连接拒绝
```

### 第 3 步：启动 Order Service

**再新开一个终端窗口**（保持所有窗口运行）：

```bash
cd /home/xiekun/claudeCode/order-service
mvn spring-boot:run
```

**预期输出**（最后几行）：
```
2026-03-25 10:31:00.000  INFO 12346 --- [           main] c.e.o.OrderServiceApplication           : Started OrderServiceApplication in 4.234 seconds (JVM running for 4.890)
```

**验证两个服务都已启动**：
```bash
# 在第三个终端运行
curl http://localhost:8082/api/users
curl http://localhost:8081/api/orders
```

**现在你有了**：
- User Service：`http://localhost:8082`
- Order Service：`http://localhost:8081`
- User Service H2 控制台：`http://localhost:8082/h2-console`
- Order Service H2 控制台：`http://localhost:8081/h2-console`

## 🧪 API 集成测试

### 测试场景：完整的订单创建流程

#### 第 1 部分：创建用户

```bash
# 创建第一个用户
curl -X POST http://localhost:8082/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "张三",
    "email": "zhangsan@example.com",
    "street": "中关村大街1号",
    "city": "北京",
    "province": "北京",
    "zipCode": "100080",
    "country": "中国"
  }'

# 复制响应中的 userId，例如：550e8400-e29b-41d4-a716-446655440000
# 在下一步中替换 <USER_ID>
```

**响应示例**：
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "张三",
  "email": "zhangsan@example.com",
  "street": "中关村大街1号",
  "city": "北京",
  "province": "北京",
  "zipCode": "100080",
  "country": "中国",
  "registeredAt": "2026-03-25T10:35:00"
}
```

#### 第 2 部分：创建订单（触发防腐层）

```bash
# 将 <USER_ID> 替换为上一步的 userId
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "P001",
        "productName": "MacBook Pro 16 inch",
        "price": 9999.99,
        "quantity": 1
      },
      {
        "productId": "P002",
        "productName": "Apple Magic Keyboard",
        "price": 299.99,
        "quantity": 2
      }
    ]
  }'
```

**这个请求触发的防腐层流程**：

```
OrderController.createOrder()
  ↓
OrderApplicationService.createOrder()
  ↓ 调用
UserServicePort.findCustomerById("550e8400...")
  ↓
UserServiceAdapter.findCustomerById()
  ↓ 通过 Feign 调用
User Service HTTP API: GET /api/users/550e8400...
  ↓ 返回
ExternalUserDTO
  ├─ userId: "550e8400..."
  ├─ username: "张三"
  ├─ email: "zhangsan@example.com"
  └─ ... (其他字段)
  ↓
UserTranslator.toCustomerInfo()
  ├─ 字段转换：username → customerName
  ├─ 结构重组：扁平地址 → CustomerAddress 值对象
  └─ 返回 CustomerInfo
  ↓ 回到应用层
Order.create(customerInfo, items)
  ↓
持久化和返回响应
```

**响应示例**：
```json
{
  "orderId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "customerName": "张三",
  "email": "zhangsan@example.com",
  "items": [
    {
      "productId": "P001",
      "productName": "MacBook Pro 16 inch",
      "unitPrice": 9999.99,
      "quantity": 1,
      "subtotal": 9999.99
    },
    {
      "productId": "P002",
      "productName": "Apple Magic Keyboard",
      "unitPrice": 299.99,
      "quantity": 2,
      "subtotal": 599.98
    }
  ],
  "status": "待支付",
  "totalAmount": 10599.97,
  "createdAt": "2026-03-25T10:36:00"
}
```

#### 第 3 部分：查询订单

```bash
# 将 <ORDER_ID> 替换为上一步响应中的 orderId
curl http://localhost:8081/api/orders/f47ac10b-58cc-4372-a567-0e02b2c3d479
```

#### 第 4 部分：支付订单

```bash
curl -X POST http://localhost:8081/api/orders/f47ac10b-58cc-4372-a567-0e02b2c3d479/pay
```

## 🐛 故障排除

### 问题 1：`mvn: command not found`

**解决方案**：Maven 未安装或未在 PATH 中

```bash
# 检查 Maven 是否安装
which mvn

# 如果未安装，使用包管理器安装
# Ubuntu/Debian:
sudo apt-get install maven

# macOS:
brew install maven

# Windows: 从 https://maven.apache.org/download.cgi 下载并安装
```

### 问题 2：`java: command not found`

**解决方案**：Java 未安装或版本过低

```bash
# 检查 Java 版本
java -version

# 如果未安装或版本过低，安装 Java 17+
# Ubuntu/Debian:
sudo apt-get install openjdk-17-jdk

# macOS:
brew install openjdk@17

# Windows: 从 https://adoptium.net 下载 OpenJDK 17
```

### 问题 3：`BUILD FAILURE` 或编译错误

```bash
# 清理 Maven 缓存并重新构建
mvn clean install -U

# 如果问题持续，检查 Java 版本
java -version  # 应该是 17 或更高

# 查看完整的构建输出（不要用 tail）
mvn clean install 2>&1 | tee build.log
```

### 问题 4：端口已被占用

**症状**：启动时出现 `Address already in use: bind`

```bash
# 查找占用端口的进程
# Linux/macOS:
lsof -i :8082
lsof -i :8081

# Windows:
netstat -ano | findstr :8082
netstat -ano | findstr :8081

# 杀死占用端口的进程（根据上面的 PID）
# Linux/macOS:
kill -9 <PID>

# Windows:
taskkill /PID <PID> /F
```

### 问题 5：`Connection refused` 当访问 Order Service API

**症状**：Order Service 启动成功，但创建订单时失败

```
com.netflix.client.ClientException: Load balancer does not have available server for client: user-service
```

**解决方案**：确保两个服务都在运行

```bash
# 在第 3 个终端检查
curl http://localhost:8082/api/users  # User Service 应该返回 200
curl http://localhost:8081/api/orders # Order Service 应该返回 200

# 如果 User Service 失败，检查其日志
# 如果 Order Service 失败，检查其日志
```

### 问题 6：`H2 Console` 无法访问

**症状**：访问 `http://localhost:8082/h2-console` 显示 404

**解决方案**：H2 Console 被禁用或未配置

```yaml
# 在 application.yml 中确保有以下配置：
spring:
  h2:
    console:
      enabled: true
```

## 📊 H2 控制台使用

### 访问 H2 Web 控制台

```
User Service: http://localhost:8082/h2-console
Order Service: http://localhost:8081/h2-console
```

### 连接信息

**User Service**：
- Driver Class: `org.h2.Driver`
- JDBC URL: `jdbc:h2:mem:userdb`
- Username: `sa`
- Password: （留空）

**Order Service**：
- Driver Class: `org.h2.Driver`
- JDBC URL: `jdbc:h2:mem:orderdb`
- Username: `sa`
- Password: （留空）

### 查询数据

```sql
-- 查看所有表
SHOW TABLES;

-- User Service
SELECT * FROM USERS;

-- Order Service
SELECT * FROM ORDERS;
SELECT * FROM ORDER_ITEMS;
```

## 🔍 观察防腐层工作

### 1. 查看日志

启动 Order Service 时，应该在日志中看到：

```
DEBUG com.example.orderservice.infrastructure.acl.userservice.adapter.UserServiceAdapter
- Calling User Service for userId: 550e8400...
```

### 2. 监控网络请求

使用浏览器开发者工具或 `curl -v`：

```bash
curl -v http://localhost:8081/api/orders
```

观察 Order Service 何时调用 User Service：

```
> GET /api/users/550e8400... HTTP/1.1
> Host: localhost:8082
< HTTP/1.1 200
< Content-Type: application/json
< {ExternalUserDTO JSON}
```

### 3. 检查异常处理

测试客户不存在的情况：

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "nonexistent-customer-id",
    "items": [...]
  }'

# 预期响应：400 Bad Request
# 错误信息：Customer not found
```

## 📝 开发工作流

### 修改代码后重新构建

```bash
# 仅重新编译（快速）
mvn compile

# 重新构建当前模块
mvn -pl user-service clean install

# 重新构建两个模块
mvn clean install

# 重新构建并跳过测试（最快）
mvn clean install -DskipTests
```

### 重启服务

```bash
# 1. 在运行服务的终端按 Ctrl+C 停止
# 2. 确认已停止（应该回到命令提示符）
# 3. 重新运行
mvn spring-boot:run
```

## 🧪 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定模块的测试
mvn -pl user-service test

# 运行特定测试类
mvn -pl order-service test -Dtest=OrderApplicationServiceTest

# 运行特定测试方法
mvn -pl order-service test -Dtest=OrderApplicationServiceTest#testCreateOrder
```

## 🎯 下一步

1. **理解防腐层**：阅读 `ACL_GUIDE.md`
2. **修改代码**：尝试修改 Translator，观察影响
3. **添加功能**：在 Order 域增加新的业务逻辑
4. **集成测试**：为防腐层编写测试
5. **部署**：学习如何容器化和部署这个应用

## 💾 项目备份和版本控制

### 初始化 Git 仓库（可选）

```bash
cd /home/xiekun/claudeCode
git init
git add .
git commit -m "Initial DDD + ACL project structure"
```

### 创建 .gitignore

```bash
# 忽略 Maven 构建输出
/target/
/**/.m2/
/**/.idea/
/**/*.iml

# 忽略 IDE 配置
.vscode/
.idea/
*.swp
*.swo

# 忽略 OS 文件
.DS_Store
.project
.classpath
```

## 📚 参考资源

- **Spring Boot**: https://spring.io/projects/spring-boot
- **Spring Cloud**: https://spring.io/projects/spring-cloud
- **OpenFeign**: https://spring.io/projects/spring-cloud-openfeign
- **DDD**: https://ddd-community.org
- **H2 Database**: https://www.h2database.com

---

**遇到问题**？查看 README.md 或 ACL_GUIDE.md 获取更多信息。
