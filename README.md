# 志愿时长管理系统

统一使用 Docker Compose 部署。

## 组成

- Spring Boot 后端
- MySQL 8
- RabbitMQ
- Elasticsearch / Logstash / Kibana

## 目录

- `src/main/java`：业务代码
- `src/main/resources/db/migration`：数据库迁移
- `src/main/resources/application-docker.yml`：Docker 配置
- `deploy/`：ELK 配置

## 接口

详细接口请看 `API.md`
