# 志愿时长管理系统 API

## 基础信息

- Base path: `/api`
- 认证方式: `Authorization: Bearer <token>`

## 用户

### 登录

- `POST /api/user/login`

请求体:

```json
{
  "studentNo": "12323020420",
  "password": "arookieofc"
}
```

### 校验 token

- `GET /api/user/verifyToken`

### 当前用户

- `GET /api/user/getUser`

### 按学号查询用户

- `GET /api/user/getUserByStudentNo?studentNo=123`

### 用户列表

- `GET /api/user/listAll`

## 活动

### 查询活动

- `POST /api/activities/query`

### 活动详情

- `GET /api/activities/{id}`

### 创建活动

- `POST /api/activities`
- `multipart/form-data`

### 更新活动

- `PUT /api/activities/{id}`
- `multipart/form-data`

### 删除活动

- `DELETE /api/activities/{id}`

### 刷新状态

- `POST /api/activities/refreshStatuses`

### 报名 / 取消报名

- `POST /api/activities/{id}/enroll`
- `POST /api/activities/{id}/unenroll`

### 审核活动

- `POST /api/activities/{id}/review?approve=true|false&reason=...`

### 我的活动 / 我的统计

- `GET /api/activities/MyActivities`
- `GET /api/activities/MyStatus`

### 导入活动

- `POST /api/activities/import`
- `multipart/form-data`

### 活动附件

- `POST /api/activities/upload/attachment`
- `DELETE /api/activities/attachment?filePath=...`
- `GET /api/activities/attachment/info?filePath=...`

## 待审核活动

- `POST /api/pending-activities/query`
- `GET /api/pending-activities`
- `GET /api/pending-activities/{id}`
- `POST /api/pending-activities/{id}/approve`
- `POST /api/pending-activities/{id}/reject?reason=...`
- `DELETE /api/pending-activities/{id}`
- `POST /api/pending-activities/batch-import`
- `GET /api/pending-activities/batch-import`
- `GET /api/pending-activities/batch-import/{batchId}`
- `POST /api/pending-activities/batch-import/{batchId}/approve`
- `POST /api/pending-activities/batch-import/{batchId}/reject?reason=...`
- `DELETE /api/pending-activities/batch-import/{batchId}`

## 文件

- `GET /api/files/download?path=...`
- `GET /api/files/preview?path=...`

## 个人时长申请

- `POST /api/activities/request_hours`
- `GET /api/activities/pending_requests`
- `POST /api/activities/review_request/{id}?approved=true|false&reason=...`
- `GET /api/activities/my_requests`
- `GET /api/activities/request/{id}`
- `DELETE /api/activities/request/{id}`

## 建议

- `POST /api/suggestions`
- `GET /api/suggestions/my`
- `GET /api/suggestions`
- `POST /api/suggestions/{id}/reply`

## 监控

- `GET /api/monitoring/dashboard`
- `GET /api/monitoring/filters`
- `GET /api/monitoring/overview`
- `POST /api/monitoring/user-stats`
- `GET /api/monitoring/logs`
- `GET /api/monitoring/developer-metrics`
- `GET /api/monitoring/developer-metrics/sse`
- `GET /api/monitoring/business-logs`
- `GET /api/monitoring/mq-task-stats`
- `POST /api/monitoring/mq-task-replay-dead`

## 说明

- `/monitoring/**` 和 `/api/monitoring/**` 都可用
- 代码中保留了若干历史兼容路由
- 健康检查走 Actuator：`/api/actuator/health`
