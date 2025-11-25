# 志愿活动管理系统 API 文档

## 概述
- 基址：`http://localhost:8080`
- 时间格式：ISO-8601 OffsetDateTime（示例：`2025-12-01T09:00:00+08:00`）
- 排序：活动列表默认按 `startTime` 降序
- 认证：统一使用请求头 `Authorization: Bearer <JWT_TOKEN>` 传递令牌（不再支持通过查询参数传递 `token`）

## 响应结构
- 所有接口统一返回：
```
{
  "code": 200,
  "message": "success",
  "data": {}
}
```
- `code`：接口语义状态码（非 HTTP 原始状态码）
- `message`：文本消息（成功为 `success`，失败为错误描述）
- `data`：业务数据（列表、对象或空）

## 数据结构
### Activity
```
{
  "id": "string",
  "functionary": "string",
  "name": "string",
  "type": "ActivityType",
  "description": "string",
  "EnrollmentStartTime": "OffsetDateTime",
  "EnrollmentEndTime": "OffsetDateTime",
  "startTime": "OffsetDateTime",
  "endTime": "OffsetDateTime",
  "CoverPath": "string",
  "CoverImage": "data:image/png;base64,......",
  "maxParticipants": 0,
  "Attachment": ["string"],
  "participants": ["student_no"],
  "status": "ActivityStatus",
  "isFull": true,
  "duration": 2.5
}
```

### ActivityStatus
- `EnrollmentNotStart`
- `EnrollmentStarted`
- `EnrollmentEnded`
- `ActivityStarted`
- `ActivityEnded`
- `UnderReview`
- `FailReview`

### ActivityType
- `COMMUNITY_SERVICE`（社区服务）
- `CULTURE_SERVICE`（文化服务）
- `EMERGENCY_RESCUE`（应急救援）
- `ANIMAL_PROTECTION`（动物保护）
- `POVERTY_ASSISTANCE`（扶贫助困）
- `ELDERLY_DISABLED_ASSISTANCE`（扶老助残）
- `MEDICAL_ASSISTANCE`（慰病助医）
- `ORPHAN_EDUCATION_ASSISTANCE`（救孤助学）

## 活动（Activities）
### 列出活动（分页与过滤）
- `GET /api/activities`
- 查询参数：
  - `page`（默认 `1`）、`pageSize`（默认 `10`）
  - `type`（`ActivityType` 枚举名）、`status`、`functionary`、`name`（支持模糊匹配）
  - `startFrom`、`startTo`（ISO-8601 OffsetDateTime）
  - `isFull`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（可选；用于识别用户角色以决定可见范围）
- 响应：
```
{
  "items": [Activity, ...],
  "total": 123,
  "page": 1,
  "pageSize": 10
}
```
- 示例：
```
GET /api/activities?page=1&pageSize=10&type=COMMUNITY_SERVICE&status=EnrollmentStarted&startFrom=2025-12-01T08:00:00+08:00&startTo=2025-12-31T18:00:00+08:00&isFull=false
```

默认行为：未指定 `status` 时，列表会排除 `UnderReview`、`FailReview`、`ActivityEnded` 三类活动；如需查看这些状态，请显式传入 `status`
- 角色视图：
  - 管理员/超级管理员：查看全部状态
  - 负责人：当查询参数 `functionary` 等于当前登录学号时，查看全部状态；否则按默认行为隐藏
  - 普通用户或未携带令牌：按默认行为隐藏

-### 创建活动
- `POST /api/activities`
- Content-Type: `multipart/form-data`
- 表单字段（按 `ActivityDTO` 模型绑定）：
  - 业务字段：`functionary`、`name`、`type`、`description`、`enrollmentStartTime`、`enrollmentEndTime`、`startTime`、`endTime`、`maxParticipants`、`attachment`、`participants`、`duration` 等
  - 文件字段：`coverFile`
- 响应：创建后的 `Activity`（状态默认为 `UnderReview`）
- 示例：
```bash
curl -X POST \
  -F "functionary=张三" \
  -F "name=社区清洁" \
  -F "type=COMMUNITY_SERVICE" \
  -F "description=街道清洁" \
  -F "enrollmentStartTime=2025-12-01T08:00:00+08:00" \
  -F "enrollmentEndTime=2025-12-01T09:00:00+08:00" \
  -F "startTime=2025-12-01T09:00:00+08:00" \
  -F "endTime=2025-12-01T12:00:00+08:00" \
  -F "maxParticipants=50" \
  -F "attachment[]=/files/a.pdf" \
  -F "attachment[]=/files/b.pdf" \
  -F "participants[]=20230001" \
  -F "participants[]=20230002" \
  -F "coverFile=@/path/to/cover.jpg" \
  http://localhost:8080/api/activities
```

-### 更新活动
- `PUT /api/activities/{id}`
- Content-Type: `multipart/form-data`
- 表单字段（按 `ActivityDTO` 模型绑定）：
  - 业务字段：同“创建活动”（字段名为小驼峰）
  - 文件字段：`coverFile`（可选；若提供将生成新的 `CoverPath` 并写库）
- 行为：
  - 当 `Attachment` 或 `participants` 为非空列表时，重建集合
  - 当其为 `null` 时，保持原有集合不变
- 响应：更新后的 `Activity`
```bash
curl -X PUT \
  -F "name=社区清洁（更新）" \
  -F "participants[]=20230003" \
  -F "coverFile=@/path/to/new-cover.png" \
  http://localhost:8080/api/activities/{id}
```

### 删除活动
- `DELETE /api/activities/{id}`
- 行为：先删除该活动的附件与参与者，再删除活动
- 响应：`{"code":200,"message":"success"}`

### 我发布的活动
- `GET /api/activities/MyActivities`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 查询参数：
  - `page`（默认 `1`）、`pageSize`（默认 `10`）
- 响应：同分页结构，返回当前登录学号为 `functionary` 的全部活动（不隐藏状态）

### 审核活动（更新状态）
- `POST /api/activities/{id}/review?approve=<true|false>`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需；仅管理员/超级管理员可调用）
- 行为：
  - 当 `approve=false`：状态更新为 `FailReview`
  - 当 `approve=true`：按当前时间与报名时间区间设置状态：
    - `now < enrollmentStartTime` → `EnrollmentNotStart`
    - `enrollmentStartTime <= now < enrollmentEndTime` → `EnrollmentStarted`
    - `now >= enrollmentEndTime` → 返回 `400 ENROLLMENT_PASSED`
  - 当报名时间缺失：返回 `400 INVALID_TIME`
- 响应：更新后的 `Activity`
  - 备注：审核通过后系统会继续通过延时队列推进到“报名结束/活动开始/活动结束”等状态

### 报名活动
- `POST /api/activities/{id}/enroll?studentNo=<学号>`
- 行为：
  - 若活动不存在：返回 `{"code":404, "message":"NOT_FOUND"}`
  - 若已报名：返回 `{"code":409, "message":"ALREADY_ENROLLED"}`
  - 若名额已满：返回 `{"code":409, "message":"CAPACITY_FULL"}`
  - 成功：返回 `{"code":200, "message":"success"}`
  
## 封面上传与路径
- 封面图片通过“创建活动/更新活动”接口的 `coverFile` 字段上传。
- 约束：
  - 文件大小最大 3MB
  - 格式：jpg、jpeg、png、gif、webp
  - 存储字段：`Activity.CoverPath` 为相对路径，长度不超过 255 字符
- 路径示例：`/covers/xxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.jpg`
- 响应对象包含 `CoverImage`（data URL），前端无需单独拉取图片。

## 用户（Users）
### 获取用户信息
- `GET /user/getUser?token=<token>`
- 行为：解析 `token` 获取用户信息（不查询数据库）
- 成功响应：
```
{
  "code": 200,
  "message": "success",
  "data": {
    "studentNo": "20230001",
    "username": "张三",
    "role": "user"
  }
}
```
- 失败响应：
  - 缺少必要信息：`{"code":500, "message":"Missing required user information in token"}`
  - 解析失败：`{"code":500, "message":"Failed to parse token: <详细错误>"}`

### 登录
- `GET /user/login?studentNo=<学号>&password=<密码>`
- 成功响应：
```
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<JWT_TOKEN>",
    "studentNo": "20230001",
    "username": "张三",
    "role": "user"
  }
}
```
- 失败响应：`{"code":500, "message":"Invalid username or password"}`

### 用户角色枚举（Role）
- `user`
- `admin`
- `functionary`
- `superAdmin`
