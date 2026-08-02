# NH 客户管理系统

一个用于作品集展示和本地学习的前后端分离客户管理系统。管理员登录后可以查询和维护客户资料，前端通过 Session Cookie 调用受保护的后端接口。

> 当前项目可用于本地演示，尚未部署上线，也不应视为已经生产可用。Phase 3“自动化测试与项目稳定化”已经完成，状态为 `complete`；Phase 4 尚未开始，状态为 `not_started`。

## 已完成功能

### 管理员认证

- 管理员登录、退出和当前登录状态查询
- 使用 Spring Security 与 `HttpSession` 保存认证状态
- 管理员密码使用 BCrypt 哈希保存
- 未登录访问客户接口时返回统一 JSON 格式的 HTTP 401
- 前端请求携带 Session Cookie，刷新页面后可以恢复登录状态

### 客户管理

- 新增、分页查询、按 ID 查询、修改和删除客户
- 按客户姓名模糊搜索
- 按客户状态筛选
- 后端分页，默认按客户 ID 倒序
- 姓名、电话、邮箱和状态的数据校验
- 统一 JSON 错误响应
- Service 层预检查重复邮箱和重复电话
- 修改客户时允许保留自己的原邮箱和电话
- MySQL 唯一约束提供最终并发重复保护
- 数据库唯一约束冲突统一映射为 HTTP 409，不向前端暴露 SQL、异常详情或堆栈

数据库唯一约束使用以下准确名称：

- `uk_customers_email UNIQUE(email)`
- `uk_customers_phone UNIQUE(phone)`

## 技术栈

| 分类 | 技术与版本 |
|---|---|
| Java | Java 17 |
| 后端框架 | Spring Boot 4.0.7 |
| Web | Spring Web MVC，由 Spring Boot 4.0.7 管理版本 |
| 持久层 | Spring Data JPA、Hibernate |
| 安全 | Spring Security、BCrypt、HttpSession |
| 数据库 | MySQL 8；当前已验证环境为 MySQL 8.4.10 |
| 数据库驱动 | MySQL Connector/J，运行时依赖，版本由 Spring Boot 管理 |
| 测试数据库 | H2，test scope；最终验证使用 H2 2.4.240 |
| 前端 | Vue `^3.5.39` |
| 构建工具 | Vite `^8.1.1`、`@vitejs/plugin-vue ^6.0.7` |
| 包管理与构建 | Maven Wrapper、npm |

Vue、Vite 和 Vue 插件版本是当前 `frontend/package.json` 中的声明范围；Spring Boot 和 Java 版本来自当前 `pom.xml`。

## 项目目录

```text
NH-Portfolio
├── README.md
├── PROJECT_MEMORY.md
├── SESSION_LOG.md
├── backend
│   └── backend
│       ├── pom.xml
│       ├── mvnw.cmd
│       └── src
└── frontend
    ├── package.json
    ├── vite.config.js
    └── src
```

- 后端实际根目录是 `backend\backend`，执行 Maven 命令时不要漏掉第二层 `backend`。
- `PROJECT_MEMORY.md` 保存项目当前长期状态。
- `SESSION_LOG.md` 保存按时间排列的开发与验证记录。
- `target`、`node_modules` 等生成目录不属于上面的核心结构。

## 环境要求

- Windows PowerShell
- JDK 17
- MySQL 8
- Node.js 和 npm

当前已验证的本地开发环境使用 Microsoft OpenJDK 17.0.19、Node.js 24.14.0 和 npm 11.9.0。`package.json` 当前没有声明 Node.js `engines` 限制。

任何真实密码都不应写入源码、Markdown、Git 或命令截图。

## 数据库准备

1. 安装并启动 MySQL 8。
2. 创建数据库 `nh_customer_manager`。
3. 由开发者自行创建独立的应用数据库用户，并授予该用户访问 `nh_customer_manager` 所需的最小权限；后端不要使用 MySQL `root` 账户运行。
4. 当前数据库用户名由 `application.properties` 中的 `spring.datasource.username` 配置，数据库密码通过 `DB_PASSWORD` 环境变量提供。
5. 确认 `customers` 表具有以下唯一约束：
   - `uk_customers_email` 对应 `email`
   - `uk_customers_phone` 对应 `phone`

当前 JPA 配置为 `spring.jpa.hibernate.ddl-auto=update`。无论表结构由 Hibernate 创建还是由开发者准备，都应核对上述约束名称和实际数据库结构。

历史数据备份表不是正常安装步骤。新建本地环境时不需要创建、复制或导入历史备份表。

## 环境变量

后端使用以下环境变量：

| 变量名 | 是否必需 | 用途 |
|---|---|---|
| `DB_PASSWORD` | 是 | MySQL 应用用户密码 |
| `ADMIN_USERNAME` | 建议显式设置 | 预置管理员用户名，长度为 3～50 个字符 |
| `ADMIN_PASSWORD` | 是 | 预置管理员密码，至少 10 个字符 |

以下仅为安全占位示例，不是真实凭据：

```powershell
$env:DB_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:ADMIN_USERNAME = "YOUR_ADMIN_USERNAME"
$env:ADMIN_PASSWORD = "YOUR_ADMIN_PASSWORD"
```

PowerShell 中通过 `$env:` 设置的变量对当前终端及其启动的后端进程生效。请在设置变量的同一个终端窗口中启动后端。

## 本地启动

### 1. 启动 MySQL

先确认 MySQL 8 正在运行，并且数据库、应用用户和唯一约束已经准备完成。

### 2. 启动后端

打开一个 Windows PowerShell 窗口：

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\backend\backend"

$env:DB_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:ADMIN_USERNAME = "YOUR_ADMIN_USERNAME"
$env:ADMIN_PASSWORD = "YOUR_ADMIN_PASSWORD"

.\mvnw.cmd spring-boot:run
```

后端默认地址：`http://localhost:8080`

可以用以下公开接口检查后端：

```text
http://localhost:8080/api/ping
```

正常响应：

```json
{"message":"pong"}
```

### 3. 启动前端

打开另一个 Windows PowerShell 窗口：

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\frontend"
npm install
npm run dev
```

前端默认地址：`http://localhost:5173`

本地前端和后端使用不同端口。后端当前允许 `http://localhost:5173` 携带 Cookie 进行跨域请求。

## API 概览

### 公开接口

| 方法 | 路径 | 功能 |
|---|---|---|
| `GET` | `/api/ping` | 后端连通性检查 |
| `POST` | `/api/auth/login` | 管理员登录并创建 Session |

### 需要登录的接口

| 方法 | 路径 | 功能 |
|---|---|---|
| `GET` | `/api/auth/me` | 查询当前登录管理员 |
| `POST` | `/api/auth/logout` | 退出登录并清除 Session |
| `GET` | `/api/customers` | 分页查询、搜索和筛选客户 |
| `GET` | `/api/customers/{id}` | 按 ID 查询客户 |
| `POST` | `/api/customers` | 新增客户，成功返回 HTTP 201 |
| `PUT` | `/api/customers/{id}` | 修改客户 |
| `DELETE` | `/api/customers/{id}` | 删除客户，成功返回 HTTP 204 |

未登录访问 `/api/auth/me`、`/api/auth/logout` 或 `/api/customers/**` 时返回 HTTP 401。

### 客户分页查询参数

`GET /api/customers` 支持：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `page` | `0` | 从 0 开始；负数会按 0 处理 |
| `size` | `10` | 后端限制到 1～100 |
| `keyword` | 空字符串 | 按客户姓名模糊搜索 |
| `status` | 空字符串 | 空值表示不筛选；有效状态为 `POTENTIAL` 或 `ACTIVE` |

查询结果固定按 `id` 倒序，分页响应包含：

- `content`
- `page`
- `size`
- `totalElements`
- `totalPages`
- `first`
- `last`

新增和修改客户时使用 JSON 请求体，客户状态只能是：

- `POTENTIAL`：潜在客户
- `ACTIVE`：正式客户

## 自动化测试

测试只使用隔离的内存 H2，不连接真实 MySQL，也不需要读取真实数据库密码。

在后端实际根目录执行：

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\backend\backend"
.\mvnw.cmd clean test
```

2026-08-02 的最终权威结果：

```text
Tests run: 36
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

测试 JDBC URL：`jdbc:h2:mem:auth_integration_test`

覆盖范围包括认证 Session、未登录 HTTP 401、客户 CRUD、校验、分页、搜索、筛选、Service 层联系方式重复检查、H2 唯一约束以及数据库冲突 HTTP 409 映射。

## 当前项目状态

| 阶段 | 状态 | 说明 |
|---|---|---|
| Phase 1：客户管理 MVP | `complete` | CRUD、搜索、筛选、分页、校验和重复检查已完成 |
| Phase 2：管理员登录与接口保护 | `complete` | Spring Security、HttpSession、登录/退出和接口保护已完成 |
| Phase 3：自动化测试与项目稳定化 | `complete` | 自动化测试与项目稳定化已经完成；后端完整测试 36 项通过，GitHub Actions 后端测试和前端构建通过 |
| Phase 4 | `not_started` | 尚未开始 |

当前边界：

- 项目尚未部署上线。
- 项目尚未声明为生产可用。
- 当前本地开发配置暂时关闭 CSRF；正式部署前必须重新设计并启用 CSRF 防护，并配置 HTTPS 和安全 Cookie。
- 当前 CORS 仅面向本地前端 `http://localhost:5173`。
- README 不包含真实密码、密码哈希、密钥或数据库凭据。
