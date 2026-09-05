# 志愿时长管理系统

一个基于 Spring Boot 的志愿活动管理和时长申请后端。

## 功能

- 用户登录与 JWT 认证
- 活动发布、查询、审核、报名
- 个人时长申请与审核
- 待审核活动与批量导入
- 意见建议
- 监控大屏、业务日志、MQ 任务监控

## 技术栈

- Spring Boot 3.3.5
- Spring Security
- MyBatis
- MySQL 8
- RabbitMQ
- Flyway
- JWT
- Swagger/OpenAPI
- WebSocket / SSE

## 运行

### 本地开发

1. 准备 JDK 17、Maven 3.6+、MySQL 8、RabbitMQ 3.12+
2. 创建数据库 `VD`
3. 修改 `src/main/resources/application.yml` 中的数据库和消息队列配置
4. 启动：

```bash
mvn spring-boot:run
```

### 一键构建

仓库提供了打包脚本：

```powershell
.\build-all.ps1
```

启动脚本入口：

```bat
start-all.bat
```

## 默认配置

- 应用端口：`8080`
- Context Path：`/api`
- Swagger：`/swagger-ui.html`
- 健康检查：`/actuator/health`

## 目录

- `src/main/java`：后端代码
- `src/main/resources/db/migration`：Flyway 迁移
- `src/test/java`：测试
- `scripts/`：运维脚本
- `build-all.ps1`：打包脚本
- `start-all.bat`：Windows 启动入口

## 说明

仓库内保留了性能压测、监控和部署相关的辅助文件，便于排障和回归验证。
