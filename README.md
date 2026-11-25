# 云集商城（yunji）

基于 Spring Cloud Alibaba 的电商微服务系统，由单体工程拆分为 9 个 Maven 模块，覆盖商品、购物车、用户、交易、支付、积分六个业务域，含独立网关与公共组件。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Java 17、Spring Boot 2.7.12 |
| 微服务 | Spring Cloud 2021.0.3、Spring Cloud Alibaba 2021.0.4.0 |
| 注册/配置 | Nacos（配置中心 + 服务发现） |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 远程调用 | OpenFeign + LoadBalancer + Sentinel 降级 |
| 数据层 | MySQL 8、MyBatis-Plus 3.5.5 |
| 缓存 | Redis、Redisson（布隆过滤器）、Caffeine |
| 消息队列 | RabbitMQ（延迟插件） |
| 安全 | JWT（RS256，JKS 密钥对）、BCrypt |
| 接口文档 | Knife4j 4.1.0 |

## 模块说明

Maven 模块统一采用 `yunji-*` 命名；**Spring 服务名保持 `cart-service` 等不变**，以兼容 Nacos 配置与网关路由。

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| `yunji-common` | — | 统一响应、全局异常、用户上下文、MyBatis 配置、MQ 常量 |
| `yunji-api` | — | 5 个 Feign 客户端、共享 DTO、降级工厂 |
| `yunji-gateway` | 18088 | 路由转发、JWT 鉴权、白名单、用户信息透传 |
| `yunji-item` | 8081 | 商品管理、库存扣减、布隆过滤器 + Redis 缓存 |
| `yunji-cart` | 8082 | 购物车增删改查、上限校验、下单消息清购物车 |
| `yunji-user` | 8084 | 登录签发 JWT、余额原子扣减 |
| `yunji-trade` | 8085 | 订单创建、超时关单、状态流转 |
| `yunji-pay` | 8086 | 支付单幂等、余额支付、支付成功消息 |
| `yunji-points` | 8087 | 积分发放（幂等 + 原子累加） |

## 核心实现

### 1. 商品查询防穿透/雪崩

「布隆过滤器 → Redis → MySQL → 回写缓存」四段式查询：启动时并行将商品 ID 灌入布隆过滤器（预期 20 万、误判率 0.01），缓存 TTL 30 分钟。

### 2. RabbitMQ 三条异步链路

- 下单成功 → 异步清购物车
- 支付成功 → 双队列扇出（更新订单状态 / 发放积分），互不抢夺
- 订单超时 → 延迟队列 30 分 30 秒后判定关单

### 3. 幂等与并发一致性

- 订单关闭：Redis `setIfAbsent` 幂等键（24h），失败删除以允许重试
- 积分发放：`ON DUPLICATE KEY UPDATE` 原子累加 + order_id 唯一索引兜底
- 支付单：`checkIdempotent` 处理已支付/已关闭/金额不一致分支
- 乐观锁：订单与支付单状态更新均带原状态条件

### 4. 统一鉴权与身份透传

网关 `AuthGlobalFilter` 校验 JWT 后注入 `user-info` 头 → 下游 `UserInfoInterceptor` 还原到 ThreadLocal → Feign 调用时 `UserInfoFeignInterceptor` 补齐头 → 请求结束清理防串号。

## 项目结构

```
yunji
├── yunji-common        # 公共组件
├── yunji-api           # Feign 接口与共享 DTO
├── yunji-gateway       # 网关
├── yunji-item          # 商品服务
├── yunji-cart          # 购物车服务
├── yunji-user          # 用户服务
├── yunji-trade         # 交易服务
├── yunji-pay           # 支付服务
├── yunji-points        # 积分服务
└── pom.xml             # 父 POM
```

## 本地启动

### 1. 环境准备

- JDK 17、Maven 3.8+
- Nacos 2.x、MySQL 8、Redis、RabbitMQ（需 `rabbitmq_delayed_message_exchange` 插件）

### 2. 初始化配置

1. 在 Nacos 创建命名空间，并导入各模块 `src/main/resources/nacos-config` 与 `nacos-config/` 下的配置：
   - `shared-redis.yaml`、`shared-rabbit.yaml`（共享配置）
   - `gateway-dev.yaml`（网关路由与鉴权）
2. 数据库按服务拆分：`hm-item`、`hm-cart`、`hm-user`、`hm-trade`、`hm-pay`、`hm-points`

### 3. 配置环境变量

复制 `.env.example` 填写实际值，或直接以环境变量注入。**请勿提交真实口令。**

### 4. 构建与运行

```bash
mvn clean package -DskipTests
# 依次启动网关与各业务服务
java -jar yunji-gateway/target/yunji-gateway.jar
```

## 说明

- 网关 JWT 使用仓库内的 `hmall.jks` 演示密钥对，生产环境请替换为自签密钥并通过 `JWT_KEYSTORE_PASSWORD` 注入口令。
- 本项目用于学习与工程实践，不含真实交易数据。
