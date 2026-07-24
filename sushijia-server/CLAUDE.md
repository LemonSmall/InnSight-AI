# 宿营家 AI SaaS 平台 - 技术文档

## 一、项目概述

**宿营家**是一个面向酒店/民宿行业的 AI SaaS 平台，为酒店提供智能内容生成（朋友圈、小红书、抖音等）、智能定价、数字营销大盘、好评引导等 AI 驱动的酒店运营能力。

- **技术栈**: Spring Boot 3.2.5 + Java 17 + MyBatis-Plus 3.5.7 + MySQL 8.0 + Redis
- **架构模式**: 多模块 Maven 多租户 SaaS 架构
- **AI 集成**: 兼容 OpenAI/DeepSeek/智谱/Moonshot/通义千问（统一 OpenAI 格式）

***

## 二、模块架构

```
sushijia-server (父 POM)
├── sushijia-common        # 通用基础组件
├── sushijia-framework     # 框架层（安全、租户、限流、审计）
├── sushijia-repository    # 数据访问层（实体 + Mapper）
├── sushijia-ai            # AI 引擎（客户端 + 模板渲染）
├── sushijia-hotel         # 酒店端业务（Controller + Service）
└── sushijia-admin         # 管理后台业务（Controller）
```

### 依赖关系····················
```
sushijia-common (无内部依赖)
    ↑
sushijia-framework → sushijia-common
    ↑
sushijia-repository → sushijia-framework, sushijia-common
    ↑
sushijia-ai → sushijia-repository
    ↑
sushijia-hotel → sushijia-ai, sushijia-repository, sushijia-framework, sushijia-common
sushijia-admin → sushijia-hotel, sushijia-repository, sushijia-framework, sushijia-common
```

***

## 三、核心模块详解

### 3.1 sushijia-common - 通用基础组件

| 包           | 类                        | 说明                                             |
| ----------- | ------------------------ | ---------------------------------------------- |
| `enums`     | `StaffRole`              | 员工角色: admin/manager/front\_desk/marketing      |
| `enums`     | `TenantTier`             | 租户套餐: trial(试用版,送500算力)/basic/pro/flagship     |
| `exception` | `BizException`           | 业务异常，携带错误码和消息                                  |
| `exception` | `GlobalExceptionHandler` | `@RestControllerAdvice` 全局异常拦截                 |
| `response`  | `R<T>`                   | 统一 API 响应体 `{code, message, data, timestamp}`  |
| `response`  | `ResultCode`             | 错误码枚举（1xxx租户/2xxx算力/3xxx登录/4xxx AI）            |
| `utils`     | `JwtUtil`                | JWT 工具类（Access Token 30min / Refresh Token 7天） |

**统一响应体** **`R<T>`**:

```java
R.ok(data)          // {code: 200, message: "success", data: ...}
R.fail(code, msg)   // {code: xxx, message: "..."}
```

**JWT Token 结构**:

- Subject: `admin:{id}` 或 `staff:{id}`
- Claims: `tenant_id`, `staff_id`, `admin_id`, `role`
- Secret 优先读取环境变量 `JWT_SECRET`

### 3.2 sushijia-framework - 框架层

#### 安全认证 (`security`)

**`SecurityConfig`**: Spring Security 配置

- 禁用 CSRF，无状态会话
- 白名单: `/api/auth/**`, `/api/admin/auth/**`, Knife4j/Swagger
- 注册 `JwtAuthFilter` 在 `UsernamePasswordAuthenticationFilter` 之前

**`JwtAuthFilter`**: JWT 认证过滤器

1. 白名单路径直接放行
2. 从 `Authorization: Bearer {token}` 提取 Token
3. 解析 Token → 注入 `TenantContext` → 放入 request attribute
4. 请求结束后清理上下文

#### 多租户 (`tenant`)

**`TenantContext`**: 租户上下文

- 使用 `TransmittableThreadLocal` 保证线程池场景不丢失
- `set()` / `get()` / `clear()` 三个静态方法

**`TenantLineHandler`**: MyBatis-Plus 多租户拦截器工厂

- 创建 `TenantLineInnerInterceptor`，自动在所有 SQL 追加 `tenant_id = ?`
- 租户 ID 列名: `tenant_id`
- 默认值: 0（无租户时）

**不隔离的表**（在 `MyBatisPlusConfig` 中配置）:
`billing_rules`, `recharge_packages`, `admins`, `prompt_templates`, `style_library`, `moderation_rules`, `audit_logs`

#### 其他框架组件

- **`AuditLog`**: 操作审计注解 `@AuditLog(action, detail)`
- **`RateLimit`**: 限流注解 `@RateLimit(key, maxCalls, windowSeconds)`

### 3.3 sushijia-repository - 数据访问层

#### 实体模型（15个）

**租户与账户**:

| 实体           | 表            | 核心字段                                                                             |
| ------------ | ------------ | -------------------------------------------------------------------------------- |
| `Tenant`     | tenants      | name, type, city, tier, status, balance, alertThreshold, meltThreshold, qpsLimit |
| `HotelStaff` | hotel\_staff | tenantId, name, phone, role, passwordHash                                        |
| `Admin`      | admins       | email, name, passwordHash, role, status                                          |

**房态管理**:

| 实体                 | 表                    | 核心字段                                                                |
| ------------------ | -------------------- | ------------------------------------------------------------------- |
| `RoomType`         | room\_types          | tenantId, name, basePrice, count                                    |
| `RoomStatus`       | room\_status         | tenantId, roomTypeId, roomNumber, status(sold/free/dirty/repair)    |
| `FutureRoomStatus` | future\_room\_status | tenantId, date, roomTypeName, occupied, available, overbooked       |
| `Guest`            | guests               | tenantId, roomTypeId, roomNumber, guestType, source, nights, status |

**算力计费**:

| 实体                | 表                  | 核心字段                                                           |
| ----------------- | ------------------ | -------------------------------------------------------------- |
| `BillingRule`     | billing\_rules     | moduleKey, moduleName, board, cost, estCostRmb, enabled        |
| `RechargePackage` | recharge\_packages | name, credits, priceRmb, applicableTiers                       |
| `CreditLedger`    | credit\_ledger     | tenantId, type(consume/recharge), amount, balanceAfter, status |

**AI 内容引擎**:

| 实体               | 表                 | 核心字段                                                                               |
| ---------------- | ----------------- | ---------------------------------------------------------------------------------- |
| `ContentTask`    | content\_tasks    | tenantId, moduleKey, inputParams(JSON), status, resultId                           |
| `ContentResult`  | content\_results  | taskId, content, tokensUsed, moderated                                             |
| `PromptTemplate` | prompt\_templates | moduleKey, version, content, modelName, status(draft/gray/production/rolled\_back) |
| `StyleLibrary`   | style\_library    | name, scope(public/private), tenantId, promptSegment                               |
| `MarketingPlan`  | marketing\_plans  | tenantId, name, festival, status, 多个JSON字段(kpis/phases/channels等)                  |
| `SystemSetting`  | system\_settings  | settingKey, settingValue                                                           |

#### Mapper 层

所有 Mapper 继承 `BaseMapper<T>`（MyBatis-Plus），部分有自定义 SQL：

| Mapper                   | 自定义方法                                            | 说明                            |
| ------------------------ | ------------------------------------------------ | ----------------------------- |
| `TenantMapper`           | `selectByIdForUpdate`                            | `SELECT ... FOR UPDATE` 行锁防并发 |
| `HotelStaffMapper`       | `findByPhone`                                    | 按手机号查询                        |
| `BillingRuleMapper`      | `findByModuleKey`                                | 按模块键查询启用的计费规则                 |
| `CreditLedgerMapper`     | `findByTenant`, `todayConsumed`                  | 流水查询 + 今日消耗统计                 |
| `FutureRoomStatusMapper` | `findByTenant`                                   | 按租户查询未来房态                     |
| `PromptTemplateMapper`   | `findProductionByModule`, `findVersionsByModule` | 模板版本管理                        |

### 3.4 sushijia-ai - AI 引擎

**`AiClient`**: 通用 AI API 客户端

- 支持 5 个 AI 供应商（OpenAI/DeepSeek/智谱/Moonshot/通义千问）
- 统一使用 OpenAI 兼容的 `chat/completions` 接口
- 配置从 `system_settings` 表读取（`ai_provider`, `ai_api_key`, `ai_model`, `ai_base_url`, `ai_max_tokens`）
- **本地回退机制**: API Key 未配置或调用失败时，自动使用内置模板生成内容

**`PromptEngine`**: 提示词模板渲染引擎

- 将 `{{variable}}` 占位符替换为实际值
- 提供 `extractVariables()` 提取模板中的变量列表

### 3.5 sushijia-hotel - 酒店端业务

#### 服务层

| Service                  | 核心功能                           |
| ------------------------ | ------------------------------ |
| `AuthService`            | 短信验证码登录（开发模式固定123456）、Token 刷新 |
| `StaffService`           | 员工 CRUD、个人信息管理、密码修改（BCrypt）    |
| `CreditService`          | 算力扣减（行锁防超扣）、充值、余额查询、熔断检查       |
| `DashboardService`       | 数字营销大盘：KPI 计算、房型统计、未来房态、在住客人   |
| `ContentGenerateService` | AI 内容生成：加载模板→渲染变量→调 AI→保存结果    |

#### 控制器层

| Controller               | 路径                 | 功能                        |
| ------------------------ | ------------------ | ------------------------- |
| `AuthController`         | `/api/auth`        | 短信发送、手机登录、Token 刷新        |
| `StaffController`        | `/api/hotel`       | 员工管理、个人信息、密码修改            |
| `DashboardController`    | `/api/hotel`       | 大盘数据（KPI + 房型 + 客人）       |
| `ContentController`      | `/api/content`     | AI 内容生成（异步任务）、任务查询、智慧大脑对话 |
| `CreditController`       | `/api/hotel`       | 算力余额、流水列表、余额检查            |
| `HotelConfigController`  | `/api/hotel`       | 酒店配置、房型管理（含房态自动创建）        |
| `PricingController`      | `/api/hotel`       | 智能定价（四因子模型）               |
| `PlanController`         | `/api/hotel/plans` | 营销方案 CRUD                 |
| `GuestServiceController` | `/api/hotel`       | 在住客人列表、好评模板生成、回评话术生成      |

### 3.6 sushijia-admin - 管理后台

| Controller               | 路径                   | 功能                       |
| ------------------------ | -------------------- | ------------------------ |
| `AdminAuthController`    | `/api/admin/auth`    | 管理员邮箱密码登录                |
| `TenantAdminController`  | `/api/admin/tenants` | 租户 CRUD、租户统计             |
| `BillingAdminController` | `/api/admin`         | 计费规则管理、充值套餐、流水查询、后台充值    |
| `PromptAdminController`  | `/api/admin`         | Prompt 模板版本管理（含回滚）、风格库管理 |
| `SettingsController`     | `/api/admin`         | 系统配置键值对（AI/短信等）          |

***

## 四、核心业务流程

### 4.1 登录认证流程

```
客户端 → POST /api/auth/sms/send {phone}
       → Redis 存储验证码 (sms:{phone}, 5min)
       → POST /api/auth/login/phone {phone, code}
       → 验证验证码 → 查询 hotel_staff → 验证租户状态
       → 生成 Access Token (30min) + Refresh Token (7天)
       → 返回 {accessToken, refreshToken, role, name, tenantId}
```

### 4.2 请求处理链路

```
HTTP Request
  → JwtAuthFilter: 提取 Token → 解析 → 注入 TenantContext
  → Spring Security: 鉴权
  → Controller: 从 TenantContext.get() 获取 tenantId
  → Service: 业务逻辑
  → Mapper: SQL 自动带 tenant_id 条件（MyBatis-Plus 租户插件）
  → Response: 统一 R<T> 格式
  → JwtAuthFilter: 清理 TenantContext
```

### 4.3 AI 内容生成流程

```
客户端 → POST /api/content/generate {module, params}
       → CreditService.deduct(): 扣减算力（行锁 + 熔断检查）
       → ContentGenerateService.submitTask(): 创建任务
       → 异步执行 executeTask():
          1. 加载 Prompt 模板（production 版本）
          2. 加载风格库（public + 租户私有，最多3个）
          3. 构建变量上下文（租户信息 + 用户参数）
          4. PromptEngine.render() 渲染模板
          5. 拼接 System Prompt + User Prompt
          6. AiClient.chat() 调用 AI API
          7. 保存 ContentResult
          8. 更新任务状态为 done
       → 返回 {taskId, balance}
       → 客户端轮询 GET /api/content/task/{taskId} 获取结果
```

### 4.4 算力计费与熔断

```
CreditService.deduct(tenantId, moduleKey, detail):
  1. 查询计费规则 → 获取 cost
  2. SELECT ... FOR UPDATE 锁定租户行（防并发）
  3. 检查余额 ≤ meltThreshold → 记录熔断流水 → 抛 ACCOUNT_MELTED
  4. 扣减余额 → 更新租户
  5. 记录消费流水
  6. 检查余额 ≤ alertThreshold → 日志预警
  7. 返回新余额
```

### 4.5 智能定价四因子模型

```
推荐价格 = basePrice × holidayMultiplier × (1 + occAdj + weatherAdj + compAdj)

因子:
- holidayMultiplier: 节假日倍率 (big=1.28, small=1.15, weekend=1.1, emotion=1.2, normal=1.0)
- occAdj: 入住率调整 (90+=+10%, 70-90=+5%, 50-70=0, 30-50=-8%, 30-=-18%)
- weatherAdj: 天气调整 (sunny=0, rain=-8%, heavy=-15%, extreme=-22%)
- compAdj: 竞争调整 (none=+5%, light=0, medium=-10%, high=-15%)
```

***

## 五、数据库设计

### 5.1 表关系概览

```
tenants (1) ──┬── (N) hotel_staff
              ├── (N) room_types ── (N) room_status
              ├── (N) future_room_status
              ├── (N) guests
              ├── (N) credit_ledger
              ├── (N) content_tasks ── (1) content_results
              ├── (N) marketing_plans
              └── (N) style_library (private scope)

billing_rules (全局，不隔离)
recharge_packages (全局，不隔离)
admins (全局，不隔离)
prompt_templates (全局，不隔离)
system_settings (全局，不隔离)
```

### 5.2 关键索引

| 表                    | 索引                                        | 用途              |
| -------------------- | ----------------------------------------- | --------------- |
| tenants              | idx\_status, idx\_tier                    | 按状态/套餐筛选        |
| hotel\_staff         | idx\_tenant, uk\_phone                    | 租户下员工查询 + 手机号唯一 |
| room\_status         | idx\_tenant\_type                         | 按租户+房型查询房态      |
| future\_room\_status | uk\_tenant\_date\_room, idx\_tenant\_date | 未来房态唯一约束 + 日期查询 |
| credit\_ledger       | idx\_tenant\_time, idx\_type\_time        | 流水按时间/类型查询      |
| prompt\_templates    | idx\_module, idx\_status                  | 按模块/状态查询模板      |

***

## 六、关键设计决策

### 6.1 多租户隔离策略

- **数据层**: MyBatis-Plus `TenantLineInnerInterceptor` 自动追加 `tenant_id` 条件
- **上下文传递**: `TransmittableThreadLocal` 保证线程池场景不丢失
- **JWT 注入**: `JwtAuthFilter` 解析 Token 后设置 `TenantContext`
- **例外表**: 全局配置表（计费规则、充值套餐、Prompt 模板等）不隔离

### 6.2 并发安全

- **算力扣减**: 使用 `SELECT ... FOR UPDATE` 行级锁，在事务内防止超扣
- **事务边界**: `@Transactional(rollbackFor = Exception.class)` 确保原子性

### 6.3 AI 服务降级

- **本地回退**: API Key 未配置或调用失败时，使用内置模板生成内容
- **多供应商**: 支持 5 个 AI 供应商，通过 `system_settings` 动态切换
- **自定义模型**: 每个 Prompt 模板可指定独立的 `model_name` 和 `max_tokens`

### 6.4 Prompt 模板版本管理

- **版本状态**: draft → gray → production / rolled\_back
- **灰度发布**: `gray_percent` 控制灰度比例
- **一键回滚**: 将目标版本设为 production，原 production 设为 rolled\_back

***

## 七、API 接口清单

### 酒店端 API

| 方法     | 路径                             | 说明         |
| ------ | ------------------------------ | ---------- |
| POST   | `/api/auth/sms/send`           | 发送短信验证码    |
| POST   | `/api/auth/login/phone`        | 手机号登录      |
| POST   | `/api/auth/token/refresh`      | 刷新 Token   |
| GET    | `/api/hotel/config`            | 获取酒店配置     |
| PUT    | `/api/hotel/config`            | 保存酒店配置     |
| GET    | `/api/hotel/rooms`             | 获取房型列表     |
| PUT    | `/api/hotel/rooms`             | 保存房型（重建房态） |
| GET    | `/api/hotel/dashboard`         | 获取大盘数据     |
| POST   | `/api/hotel/pricing/recommend` | 智能定价建议     |
| GET    | `/api/hotel/credits/balance`   | 算力余额       |
| GET    | `/api/hotel/credits/ledger`    | 算力流水       |
| GET    | `/api/hotel/credits/check`     | 余额检查       |
| POST   | `/api/content/generate`        | 提交 AI 生成任务 |
| GET    | `/api/content/task/{id}`       | 查询任务状态     |
| POST   | `/api/content/brain/chat`      | 智慧大脑对话     |
| GET    | `/api/hotel/guests`            | 在住客人列表     |
| POST   | `/api/hotel/review/generate`   | 生成好评模板     |
| POST   | `/api/hotel/reply/generate`    | 生成回评话术     |
| GET    | `/api/hotel/plans`             | 营销方案列表     |
| POST   | `/api/hotel/plans`             | 创建营销方案     |
| PUT    | `/api/hotel/plans/{id}`        | 更新营销方案     |
| DELETE | `/api/hotel/plans/{id}`        | 删除营销方案     |
| GET    | `/api/hotel/staff`             | 员工列表       |
| POST   | `/api/hotel/staff`             | 新增员工       |
| PUT    | `/api/hotel/staff/{id}`        | 更新员工       |
| DELETE | `/api/hotel/staff/{id}`        | 删除员工       |
| GET    | `/api/hotel/profile`           | 个人信息       |
| PUT    | `/api/hotel/profile`           | 修改个人信息     |
| PUT    | `/api/hotel/password`          | 修改密码       |

### 管理后台 API

| 方法   | 路径                                 | 说明          |
| ---- | ---------------------------------- | ----------- |
| POST | `/api/admin/auth/login`            | 管理员登录       |
| GET  | `/api/admin/tenants`               | 租户列表        |
| GET  | `/api/admin/tenants/{id}`          | 租户详情        |
| POST | `/api/admin/tenants`               | 创建租户        |
| PUT  | `/api/admin/tenants/{id}`          | 更新租户        |
| GET  | `/api/admin/tenants/stats`         | 租户统计        |
| GET  | `/api/admin/billing-rules`         | 计费规则列表      |
| PUT  | `/api/admin/billing-rules/{id}`    | 更新计费规则      |
| GET  | `/api/admin/packages`              | 充值套餐列表      |
| POST | `/api/admin/packages`              | 创建充值套餐      |
| GET  | `/api/admin/ledger`                | 流水列表        |
| POST | `/api/admin/tenants/{id}/recharge` | 后台充值        |
| GET  | `/api/admin/prompts`               | Prompt 模板列表 |
| GET  | `/api/admin/prompts/{moduleKey}`   | 模板版本列表      |
| POST | `/api/admin/prompts`               | 创建模板        |
| PUT  | `/api/admin/prompts/{id}`          | 更新模板        |
| PUT  | `/api/admin/prompts/{id}/rollback` | 模板回滚        |
| GET  | `/api/admin/styles`                | 风格库列表       |
| POST | `/api/admin/styles`                | 创建风格        |
| PUT  | `/api/admin/styles/{id}`           | 更新风格        |
| GET  | `/api/admin/settings`              | 系统配置        |
| PUT  | `/api/admin/settings`              | 保存系统配置      |

***

## 八、错误码体系

| 范围        | 分类      | 示例                                                          |
| --------- | ------- | ----------------------------------------------------------- |
| 200       | 成功      | SUCCESS                                                     |
| 400-409   | 通用客户端错误 | BAD\_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT\_FOUND, CONFLICT |
| 500       | 服务器错误   | SERVER\_ERROR                                               |
| 1001-1002 | 租户      | TENANT\_NOT\_FOUND, TENANT\_SUSPENDED                       |
| 2001-2002 | 算力      | INSUFFICIENT\_CREDIT, ACCOUNT\_MELTED                       |
| 3001-3003 | 登录      | LOGIN\_FAILED, SMS\_SEND\_FAILED, SMS\_CODE\_EXPIRED        |
| 4001-4003 | AI      | AI\_GENERATE\_FAILED, AI\_TIMEOUT, CONTENT\_MODERATED       |

***

## 九、配置说明

### 环境变量

| 变量           | 说明       | 默认值                                                         |
| ------------ | -------- | ----------------------------------------------------------- |
| `JWT_SECRET` | JWT 签名密钥 | `sushijia-ai-hotel-platform-secret-key-2026-must-be-longer` |

### 系统配置（system\_settings 表）

| Key             | 说明         | 默认值       |
| --------------- | ---------- | --------- |
| `ai_provider`   | AI 供应商     | `openai`  |
| `ai_api_key`    | API Key    | 空（触发本地回退） |
| `ai_model`      | 模型名称       | `gpt-4o`  |
| `ai_base_url`   | 自定义 API 地址 | 空         |
| `ai_max_tokens` | 最大 Token 数 | `4000`    |
| `sms_*`         | 短信相关配置     | 空         |

### 默认计费规则

| 模块         | 算力消耗 | 估算成本(元) |
| ---------- | ---- | ------- |
| 房态图AI识别    | 0    | 0.02    |
| 运营智慧大脑（每轮） | 5    | 0.06    |
| 智能定价建议     | 0    | 0.00    |
| 朋友圈文案      | 8    | 0.10    |
| 小红书图文      | 10   | 0.12    |
| 视频口播文案     | 12   | 0.18    |
| 营销海报生成     | 30   | 0.45    |
| AI修图       | 20   | 0.30    |
| 公众号推文      | 15   | 0.25    |
| 个性化好评模板    | 6    | 0.08    |
| AI回评话术     | 8    | 0.10    |

### 充值套餐

| 名称  | 算力    | 价格(元) | 适用套餐          |
| --- | ----- | ----- | ------------- |
| 体验包 | 500   | 150   | trial         |
| 标准包 | 2000  | 560   | basic, pro    |
| 畅享包 | 5000  | 1300  | pro, flagship |
| 连锁包 | 20000 | 4800  | flagship      |

