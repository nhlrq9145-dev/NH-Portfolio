# NH 客户管理系统

一个用于作品集展示和本地学习的前后端分离客户管理系统。管理员登录后可以查询和维护客户资料，前端通过 Session Cookie 调用受保护的后端接口。

> 当前项目可用于本地演示，尚未部署上线，也不应视为已经生产可用。Phase 3“自动化测试与项目稳定化”已经完成，状态为 `complete`；Phase 4 状态为 `in_progress`，其中 Phase 4.1“管理员客户 DTO 边界”、Phase 4.2“安全公开 Demo 客户接口”、Phase 4.3.1“后端 CORS 允许来源外部化，并收敛为单一精确来源”和 Phase 4.3.2“前端 API 基址外部化与本地开发代理兼容”均已完成。Phase 4.3 继续为 `in_progress`，Phase 4.3.3 为 `not_started`。

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
- 管理员客户接口使用独立请求和响应 DTO，Controller 公开方法不再接收或返回 JPA Entity
- 客户请求只绑定姓名、电话、邮箱和状态；ID 与创建时间由后端控制

数据库唯一约束使用以下准确名称：

- `uk_customers_email UNIQUE(email)`
- `uk_customers_phone UNIQUE(phone)`

### 公开 Demo 客户接口

- 公开支持 `GET /api/demo/customers` 和 `HEAD /api/demo/customers`
- Demo 响应只包含 `displayName`、`industry`、`status`
- 数据全部是在代码中固定声明、并以“演示客户”明确标记的虚构数据
- Demo Controller 不访问 `CustomerRepository`、`CustomerService`、`Customer` Entity 或数据库
- Demo 命名空间的 `POST`、`PUT`、`PATCH`、`DELETE` 均返回 HTTP 403；规范路径、编码路径和矩阵参数路径均受到保护
- 匿名 Demo 写请求不创建 Session，也不返回 `Set-Cookie` 或 `JSESSIONID`
- Demo HEAD 返回 HTTP 200、正文为空，并且不创建 Session 或 Cookie
- CORS 允许来源由 `APP_CORS_ALLOWED_ORIGIN` 配置；未设置时默认使用 `http://localhost:5173`，只注册一个规范化后的精确 Origin，允许方法已支持 HEAD
- 匿名 `HEAD /api/customers` 仍返回 HTTP 401

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
| `APP_CORS_ALLOWED_ORIGIN` | 否 | 后端允许的唯一前端 Origin，默认值为 `http://localhost:5173` |

`APP_CORS_ALLOWED_ORIGIN` 必须填写单个、完整、精确的 HTTP 或 HTTPS Origin，只能包含协议、主机和可选合法端口。多个来源、通配符或通配子域、路径、查询参数、fragment、user-info、非法端口和非法协议都会被拒绝；不能使用 `"*"`。校验成功后，scheme 和 host 会规范为小写，HTTP 的默认端口 80 与 HTTPS 的默认端口 443 会被移除，合法非默认端口会保留。不要将真实部署域名、密码或其他秘密写入仓库。

Controller 级 `@CrossOrigin` 已移除，所有接口统一使用上述全局单一 Origin 契约。Demo 集成测试固定使用测试专用的 `http://localhost:5173`，不受开发者机器上的 `APP_CORS_ALLOWED_ORIGIN` 环境变量影响。

前端构建可使用以下公开环境变量：

| 变量名 | 是否必需 | 用途 |
|---|---|---|
| `VITE_API_BASE_URL` | 否 | 完整 API 根地址；未设置、空字符串或纯空白时默认使用相对路径 `/api` |

`VITE_API_BASE_URL` 表示包含固定 `/api` 路径的完整 API 根地址。允许值为 `/api`、`/api/`，或路径严格为 `/api` 或 `/api/` 的绝对 HTTP/HTTPS URL；末尾斜杠会被移除。其他路径、查询参数、fragment、user-info、协议相对 URL 和非 HTTP/HTTPS 协议会在应用初始化时抛出配置错误，不会静默回退。`APP_CORS_ALLOWED_ORIGIN` 控制后端允许的前端 Origin，`VITE_API_BASE_URL` 控制前端请求的后端 API 根地址，两者语义不同。

所有 `VITE_` 环境变量都会进入公开的前端构建产物。不得将密码、Token、密钥或其他秘密放入 `VITE_API_BASE_URL` 或任何其他 `VITE_` 环境变量。

以下仅为安全占位示例，不是真实凭据：

```powershell
$env:DB_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:ADMIN_USERNAME = "YOUR_ADMIN_USERNAME"
$env:ADMIN_PASSWORD = "YOUR_ADMIN_PASSWORD"
$env:APP_CORS_ALLOWED_ORIGIN = "http://localhost:5173"
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

未配置 `VITE_API_BASE_URL` 时，前端请求相对路径 `/api`；Vite 开发服务器将 `/api` 原样代理到 `http://localhost:8080`，因此现有六处请求在本地开发时无需改写。若显式配置绝对 API 根地址，后端的 `APP_CORS_ALLOWED_ORIGIN` 必须与前端 Origin 匹配。

## 前端生产构建

默认生产构建保留相对 API 根地址 `/api`。如果前端和后端不使用同一 Origin，应在执行构建的终端中将 `VITE_API_BASE_URL` 设置为公开的绝对 API 根地址，例如 `https://api.example.com/api`；修改后需要重新构建。

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\frontend"
npm run build
```

## API 概览

### 公开接口

| 方法 | 路径 | 功能 |
|---|---|---|
| `GET` | `/api/ping` | 后端连通性检查 |
| `POST` | `/api/auth/login` | 管理员登录并创建 Session |
| `GET` | `/api/demo/customers` | 返回固定的虚构 Demo 客户数据 |
| `HEAD` | `/api/demo/customers` | 检查公开 Demo 资源，响应正文为空 |

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

前端配置契约测试：

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\frontend"
npm test
```

后端测试只使用隔离的内存 H2，不连接真实 MySQL，也不需要读取真实数据库密码。

在后端实际根目录执行：

```powershell
Set-Location "C:\Users\NH\Desktop\NH-Portfolio\backend\backend"
.\mvnw.cmd clean test
```

2026-08-11 的当前结果：

```text
Tests run: 111
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

完整后端测试只使用隔离内存 H2；Surefire 证据中 `jdbc:mysql` 命中数为 0，未连接真实 MySQL。

测试 JDBC URL 包括 `jdbc:h2:mem:auth_integration_test`、`jdbc:h2:mem:demo_customer_api_test` 和 `jdbc:h2:mem:cors_configuration_test`。

覆盖范围包括认证 Session、未登录 HTTP 401、客户 CRUD、DTO 请求与响应契约、校验、分页、搜索、筛选、Service 层联系方式重复检查、H2 唯一约束、数据库冲突 HTTP 409 映射，以及 Demo GET/HEAD、CORS 单一精确 Origin 的校验与规范化、路径保护和无状态写请求。

Phase 4.3.1 最终 Review 结论：`未发现会导致 Phase 4.3.1 验收失败的 P1/P2 问题`。

## 当前项目状态

| 阶段 | 状态 | 说明 |
|---|---|---|
| Phase 1：客户管理 MVP | `complete` | CRUD、搜索、筛选、分页、校验和重复检查已完成 |
| Phase 2：管理员登录与接口保护 | `complete` | Spring Security、HttpSession、登录/退出和接口保护已完成 |
| Phase 3：自动化测试与项目稳定化 | `complete` | 自动化测试与项目稳定化已经完成；GitHub Actions 后端测试和前端构建通过 |
| Phase 4 | `in_progress` | Phase 4.1、Phase 4.2、Phase 4.3.1 和 Phase 4.3.2 已完成；Phase 4 整体不得标记为 complete |
| Phase 4.1：管理员客户 DTO 边界 | `complete` | 管理端请求/响应 DTO 边界及服务器字段保护已经完成 |
| Phase 4.2：安全公开 Demo 客户接口 | `complete` | Demo GET/HEAD、写请求 403、路径保护、CORS 与无状态行为已经完成并通过复审 |
| Phase 4.3 | `in_progress` | Phase 4.3.1、Phase 4.3.2 已完成；Phase 4.3.3 尚未开始 |
| Phase 4.3.1：后端 CORS 允许来源外部化，并收敛为单一精确来源 | `complete` | 配置外部化、非法 Origin 拒绝、规范化与测试环境隔离均已完成并通过复审 |
| Phase 4.3.2：前端 API 基址外部化与本地开发代理兼容 | `complete` | API 基址外部化、本地 `/api` 开发代理兼容、配置测试与生产构建均已完成；浏览器联调未执行 |
| Phase 4.3.3 | `not_started` | 尚未开始；范围、目标、最小任务和验收标准待只读定义 |

当前边界：

- 项目尚未部署上线。
- 项目尚未声明为生产可用。
- 当前本地开发配置暂时关闭 CSRF；正式部署前必须重新设计并启用 CSRF 防护，并配置 HTTPS 和安全 Cookie。
- 当前 CORS 默认面向本地前端 `http://localhost:5173`；如需覆盖，只能通过 `APP_CORS_ALLOWED_ORIGIN` 配置一个精确的 HTTP/HTTPS Origin。
- README 不包含真实密码、密码哈希、密钥或数据库凭据。
