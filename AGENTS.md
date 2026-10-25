# AGENTS.md — 社区综合事务管理系统（后端）

本科毕业设计《基于SpringBoot的社区综合事务管理系统的设计与实现》的后端服务。
技术栈：Spring Boot 3.4.12 / Java 21 / MyBatis-Plus / Spring Security + JWT / MySQL 8.4 / Redis (Lua) / Elasticsearch 9.2 (IK) / MinIO / WebSocket (STOMP) / EasyExcel / Knife4j。

配套前端工程（独立仓库）：`/Users/li/Code/JS/VueProjects/community-affairs-vue`（Vue 3 + Vite，**包管理器是 pnpm**）。前端改动需在该工程执行 `pnpm build`，把 `dist/` 产物覆盖到本仓库 `./html/`（Nginx 直出的打包产物，已入库；覆盖后浏览器刷新即生效，无需重启容器，git 中旧 hash 资源显示删除/新增属正常）。

## 常用命令

```bash
cp .env.example .env                                   # 首次必做：填 MYSQL_PASSWORD / REDIS_PASSWORD / MINIO_* / JWT_SECRET
docker compose up -d ca-mysql ca-redis ca-es ca-minio ca-nginx-dev   # 本地开发模式：中间件容器 + 开发版 Nginx（反代到宿主机 8082）
./mvnw spring-boot:run                                 # 本地启动后端（需本机 JDK 21）
./mvnw clean package                                   # 打包
DOCKER_BUILDKIT=1 docker compose up -d --build         # 全容器模式（生产）
docker exec -i ca-mysql mysql -uroot -p"${MYSQL_PASSWORD}" < init/mysql/community_affairs.sql  # 手动重建库表
```

- 访问入口统一走 `http://localhost:8080`（Nginx）；Knife4j 文档 `/doc.html`，后端直连 `127.0.0.1:8082`。
- `JWT_SECRET` 未设置时**启动即失败**（强制安全约束，无默认值）。
- 目前没有单元测试（`src/test` 为空），验证以启动 + 接口调试为准。
- 数据库 DDL 与种子数据在 `init/mysql/community_affairs.sql`：仅在 MySQL 数据卷**首次初始化**时自动执行；脚本可重复执行但会重建全部表。

## 目录与分层

包根 `com.li.community`：
- `controller/` REST 接口 → `service/` + `service/impl/` 业务 → `mapper/`（MyBatis-Plus）/ `repository/`（Elasticsearch）数据访问
- `model/entity|dto|vo|document`：DB 实体 / 请求入参 / 响应出参 / ES 文档，新增接口按此归类
- `config/`：SecurityConfig、WebSocketConfig、RedisConfig、MinioConfig、MybatisPlusConfig、OpenApiConfig、DatabaseInitializer
- `common/`：`result/Result`（统一响应包装）、`exception/BizException + ErrorCode + GlobalExceptionHandler`、`security/LoginUser + CommunityScopeResolver`、`utils/`（DfaFilterUtil / JwtUtils / MinioUtil / QRCodeUtil / SecurityUtils）、`annotation/LogOperation`（操作审计）
- `aspect/` 审计日志切面、`filter/` JWT 过滤器、`task/` 定时任务（如超时自动好评结单）
- `src/main/resources/lua/activity_signup.lua`：活动报名 Redis Lua 原子防超卖脚本

## 关键规则

1. **权限矩阵三处同步**：管理端接口的真实权限规则是控制器方法上的 `@PreAuthorize("hasAnyRole(...)")`（已启用 `@EnableMethodSecurity`），对应前端 `community-affairs-vue/src/constants/permissions.ts` 与 `README.md` 第五章接口级权限矩阵。改动任一侧必须同步其余两侧。
2. **网格员楼栋数据隔离**：工单列表/详情/初审、房产认证列表/审批在服务层按 `biz_building.grid_officer_id` 强制过滤为本人负责楼栋；其他管理角色不受限。涉工单/房产查询的改动不能绕过该过滤。
3. 统一返回 `Result<T>`；业务错误抛 `BizException(ErrorCode)`，由全局异常处理器兜底，不要在 controller 手写错误分支。
4. MyBatis-Plus 全局约定：逻辑删除字段 `deleted`（1 删除 / 0 正常）、主键 `assign_id`、下划线转驼峰。
5. 管理端接口路径统一含 `/admin/`（`/api/*/admin/**`），SecurityConfig 中的 URL 规则是四类工作人员（超管/社区/物业/网格）的兜底闸门，新增管理接口保持该路径约定。
6. 敏感词过滤基于内存 DFA 前缀树（`DfaFilterUtil`），词库增删需热重载前缀树；邻里帖/评论内容必须过 DFA。
7. 文件上传统一走 `FileService`（秒传）→ `MinioUtil`（桶 `community-files` 自动创建，单文件 20MB 上限，multipart 与 Nginx `client_max_body_size` 已对齐）。按内容 SHA-256 在 `sys_file` 登记表去重（相同内容全桶仅一份，秒传命中直接复用 URL）；`FileCleanupTask` 定时删除未被业务表引用且超 `file.cleanup.retention-hours`（默认 168h）宽限期的对象。**新增存文件 URL 的表字段时，必须同步加进 `FileServiceImpl.REFERENCE_COLUMNS` 引用扫描清单，否则该字段的图片会被清理任务误删。**

## 环境与坑

- `.env` 已 gitignore；`application.yaml` 通过 `spring.config.import: optional:file:.env` 自动读取项目根目录 `.env`。
- MySQL 密码只在数据卷首次初始化时写入；改 `.env` 密码后需重建卷或手动 ALTER（README FAQ Q2）。
- 与独立的 `dev-databases` 中间件栈端口冲突（3306/6379/9000/9001/9200），两套容器栈不可同时运行。
- `ca-es` 镜像是本地构建的（ES 9.2.0 + IK 插件），compose 启动加 `--build`；Linux/WSL2 需 `vm.max_map_count=262144`。
- 跨机部署时 `.env` 的 `MINIO_PUBLIC_ENDPOINT` 必须改为 `http://<服务器IP>:9000`，否则图片破图（FAQ Q7）。
- 涉及部署/排错的完整说明（六大服务、双 Nginx 环境、FAQ）都在 `README.md`，改动 Dockerfile / docker-compose / nginx 配置前先读它。
