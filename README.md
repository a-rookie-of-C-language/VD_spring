# 🎓 志愿时长管理系统

一个基于 Spring Boot 的志愿活动管理和时长申请系统，支持活动发布、报名管理、个人时长申请等功能。

## 🌟 主要功能

- 👥 **用户管理**: 支持学生、负责人、管理员角色
- 📅 **活动管理**: 活动发布、编辑、审核、状态跟踪
- 📝 **报名系统**: 在线报名、取消报名、人数限制
- ⏰ **个人申请**: 个人志愿时长申请和审核
- 📊 **数据统计**: 个人时长统计、活动参与情况
- 🔔 **消息通知**: 基于 RabbitMQ 的异步消息处理
- 📈 **监控大屏**: 系统监控和数据可视化

## 🛠️ 技术栈

- **后端**: Spring Boot 3.3.5, Spring Security, MyBatis
- **数据库**: MySQL 8.0, Flyway (数据库迁移)
- **消息队列**: RabbitMQ (延迟队列插件)
- **缓存**: Redis (可选)
- **认证**: JWT Token
- **文档**: Swagger/OpenAPI 3
- **容器化**: Docker, Docker Compose

## 🚀 快速开始

### 方式一: Docker 部署 (推荐)

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd volunteer-duration
   ```

2. **启动服务**
   ```bash
   # Windows
   start.bat
   
   # Linux/Mac
   chmod +x start.sh && ./start.sh
   ```

3. **验证部署**
   ```bash
   # Windows
   test.bat
   
   # Linux/Mac  
   chmod +x test.sh && ./test.sh
   ```

### 方式二: 本地开发

1. **环境准备**
   - JDK 17+
   - Maven 3.6+
   - MySQL 8.0
   - RabbitMQ 3.12+ (需要延迟队列插件)

2. **数据库配置**
   ```sql
   CREATE DATABASE VD CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

3. **修改配置**
   ```yaml
   # src/main/resources/application.yml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/VD?...
       username: root
       password: your_password
   ```

4. **启动应用**
   ```bash
   mvn spring-boot:run
   ```

## 📋 服务信息

| 服务 | 端口 | 用户名 | 密码 | 说明 |
|------|------|--------|------|------|
| 应用 | 8080 | - | - | 主应用 |
| MySQL | 3306 | root | su201314 | 数据库 |
| RabbitMQ | 5672/15672 | vd_user | su201314 | 消息队列 |
| Redis | 6379 | - | - | 缓存 (可选) |

## 🧪 测试账号

| 角色 | 学号          | 密码 | 权限 |
|------|-------------|------|------|
| 超级管理员 | 12323020420 | arookieofc | 全部权限 |
| 负责人 | 12323020421 | arookieofc | 发布活动、查看统计 |
| 学生 | 12323020406 | arookieofc | 报名活动、申请时长 |
| 学生 | 12323020334 | arookieofc | 报名活动、申请时长 |

## 🔗 重要链接

- 📖 **API 文档**: http://localhost:8080/swagger-ui.html
- 📊 **健康检查**: http://localhost:8080/api/monitoring/health
- 🐰 **RabbitMQ 管理**: http://localhost:15672
- 📋 **API 详细文档**: [API.md](API.md)

## 📁 项目结构

```
src/
├── main/
│   ├── java/site/arookieofc/
│   │   ├── controller/          # 控制器层
│   │   ├── service/             # 业务逻辑层
│   │   ├── dao/                 # 数据访问层
│   │   ├── security/            # 安全配置
│   │   └── configuration/       # 配置类
│   └── resources/
│       ├── db/migration/        # 数据库迁移脚本
│       └── application*.yml     # 配置文件
├── docker/                      # Docker 配置
├── docker-compose.yml          # 容器编排
├── Dockerfile                  # 应用镜像
└── start.sh/start.bat         # 启动脚本
```

## 🔄 主要 API 接口

### 用户认证
 `POST /user/login` - 用户登录
- `GET /user/getUser` - 获取当前用户信息

### 活动管理  
- `GET /api/activities` - 获取活动列表 (支持分页和筛选)
- `POST /api/activities` - 创建活动
- `PUT /api/activities/{id}` - 更新活动
- `DELETE /api/activities/{id}` - 删除活动
- `POST /api/activities/{id}/enroll` - 报名活动
- `POST /api/activities/{id}/unenroll` - 取消报名

### 个人时长申请
- `POST /api/activities/request_hours` - 提交个人时长申请
- `GET /api/activities/my_requests` - 获取我的申请记录
- `GET /api/activities/pending_requests` - 获取待审核申请 (管理员)
- `POST /api/activities/review_request/{id}` - 审核申请 (管理员)

### 监控统计
- `GET /api/monitoring/dashboard` - 监控大屏数据
- `GET /api/monitoring/overview` - 系统概览
- `GET /api/activities/MyStatus` - 我的志愿时长统计

详细接口文档请参考 [API.md](API.md)

## 🐳 Docker 部署详情

完整的 Docker 部署指南请参考 [DOCKER.md](DOCKER.md)

包含以下服务：
- Spring Boot 应用 (包含延迟队列功能)
- MySQL 8.0 数据库
- RabbitMQ (带延迟队列插件)
- Redis 缓存
- 数据持久化 Volumes

## 🔧 开发指南

### 添加新功能
1. 在 `src/main/resources/db/migration/` 下创建新的 SQL 迁移文件
2. 创建对应的 Entity, DTO, Mapper 
3. 实现 Service 业务逻辑
4. 创建 Controller 处理 HTTP 请求
5. 更新 API 文档

### 数据库迁移
使用 Flyway 管理数据库版本：
- `V1__init.sql` - 初始化表结构
- `V2__add_import_and_pending_tables.sql` - 添加导入功能
- `V3__add_user_classification_fields.sql` - 用户分类字段
- `V4__add_hour_requests_table.sql` - 个人时长申请

### 配置文件
- `application.yml` - 开发环境配置
- `application-docker.yml` - Docker 环境配置
- `application-prod.yml` - 生产环境配置

## 🔒 安全说明

- 使用 JWT Token 进行身份认证
- 密码使用 BCrypt 加密存储
- 支持角色基础的权限控制
- API 接口有相应的权限验证

## 🤝 贡献指南

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 支持

如有问题或建议，请：
1. 查看 [DOCKER.md](DOCKER.md) 排除部署问题
2. 查看 [API.md](API.md) 了解接口详情  
3. 提交 Issue 或 Pull Request

---

🎉 **Happy Coding!** 感谢使用志愿时长管理系统！
