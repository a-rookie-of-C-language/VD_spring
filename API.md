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
  "expectedEndTime": "OffsetDateTime",
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
  - 返回 `ActivityPageVO`：包含 `items`、`total`、`page`、`pageSize`
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
- 行为：
  - 活动创建成功后，负责人（`functionary`）会自动被添加为参与者
  - 如果 `participants` 列表中包含负责人学号，不会重复添加
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
 - 示例：
 ```bash
 curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/activities/MyActivities?page=1&pageSize=10"
 ```

### 我的活动状态
- `GET /api/activities/MyStatus`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 行为：返回当前登录用户参与的活动列表、总时长和总活动数
- 响应：
```
{
  "totalDuration": 2.5,
  "totalActivities": 1,
  "activities": [Activity, ...]
}
```
- 示例：
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/activities/MyStatus"
```

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
 - 示例：
 ```bash
 # 审核通过
 curl -X POST -H "Authorization: Bearer <JWT_TOKEN>" \
   "http://localhost:8080/api/activities/{id}/review?approve=true"

 # 审核拒绝
 curl -X POST -H "Authorization: Bearer <JWT_TOKEN>" \
   "http://localhost:8080/api/activities/{id}/review?approve=false"
 ```

-### 报名活动
- `POST /api/activities/{id}/enroll`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 行为：
  - 若活动不存在：返回 `{"code":404, "message":"NOT_FOUND"}`
  - 若已报名：返回 `{"code":409, "message":"ALREADY_ENROLLED"}`
  - 若名额已满：返回 `{"code":409, "message":"CAPACITY_FULL"}`
  - 成功：返回 `{"code":200, "message":"success"}`

-### 取消报名
- `POST /api/activities/{id}/unenroll`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 行为：仅在报名结束前允许取消报名（`now < EnrollmentEndTime`）
  - 若活动不存在：返回 `{"code":404, "message":"NOT_FOUND"}`
  - 若当前用户是活动负责人：返回 `{"code":403, "message":"FUNCTIONARY_CANNOT_UNENROLL"}`（负责人不能退出自己负责的活动）
  - 若已过报名结束时间：返回 `{"code":400, "message":"ENROLLMENT_ENDED"}`
  - 若未报名：返回 `{"code":409, "message":"NOT_ENROLLED"}`
  - 成功：返回 `{"code":200, "message":"success"}`
## 封面上传与路径
- 封面图片通过“创建活动/更新活动”接口的 `coverFile` 字段上传。
- 约束：
  - 文件大小最大 20MB
  - 格式：jpg、jpeg、png、gif、webp
  - 存储字段：`Activity.CoverPath` 为相对路径，长度不超过 255 字符
- 路径示例：`/covers/xxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.jpg`
- 响应对象包含 `CoverImage`（data URL），前端无需单独拉取图片。

## 用户（Users）
### 获取用户信息
- `GET /user/getUser`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 行为：基于认证主体返回当前登录用户信息
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
  - 未认证：`{"code":401, "message":"UNAUTHORIZED"}`

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

## 监控（Monitoring）
- 权限：仅超级管理员可访问；请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）

### 监控大屏数据
- `GET /api/monitoring/dashboard`
- 查询参数：
  - `timeRange`：时间范围（可选，默认 `monthly`）
    - `daily`：当天数据
    - `weekly`：最近7天数据
    - `monthly`：最近30天数据
    - `yearly`：最近365天数据
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "overview": {
      "totalUsers": 1250,              // 总用户数
      "totalActivities": 85,           // 总活动数
      "completedActivities": 68,       // 已完成活动数
      "totalDuration": 3450.5,         // 总志愿时长
      "totalParticipants": 3200,       // 总参与人次
      "averageDuration": 2.8,          // 平均志愿时长
      "newActivities": 12,             // 时间范围内新增活动数
      "activeUsers": 150               // 时间范围内活跃用户数
    },
    "classificationStats": {
      "byGrade": [
        {
          "name": "2023级",
          "userCount": 300,              // 用户数
          "activityCount": 45,           // 参与活动数
          "totalHours": 850.5,           // 总时长
          "averageHours": 2.8            // 平均时长
        },
        {
          "name": "2022级",
          "userCount": 280,
          "activityCount": 42,
          "totalHours": 780.0,
          "averageHours": 2.8
        }
      ],
      "byCollege": [
        {
          "name": "计算机学院",
          "userCount": 450,
          "activityCount": 68,
          "totalHours": 1250.5,
          "averageHours": 2.8
        },
        {
          "name": "外国语学院",
          "userCount": 380,
          "activityCount": 55,
          "totalHours": 980.0,
          "averageHours": 2.6
        }
      ],
      "byClazz": [
        {
          "name": "计算机2301班",
          "userCount": 45,
          "activityCount": 12,
          "totalHours": 125.5,
          "averageHours": 2.8
        },
        {
          "name": "软件2301班",
          "userCount": 42,
          "activityCount": 11,
          "totalHours": 118.0,
          "averageHours": 2.8
        }
      ]
    },
    "activityTypes": [
      { "name": "COMMUNITY_SERVICE", "value": 24 },
      { "name": "CULTURE_SERVICE", "value": 15 },
      ...
    ],
    "topUsers": [
      { "rank": 1, "studentNo": "20230001", "name": "张三", "hours": 120.5 },
      { "rank": 2, "studentNo": "20230002", "name": "李四", "hours": 110.0 },
      ...
    ],
    "growthRanking": [
      { "rank": 1, "studentNo": "20230003", "name": "王五", "hours": 50.0 },
      ...
    ]
  }
}
```
- 示例：
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/monitoring/dashboard?timeRange=monthly"
```

### 获取筛选选项
- `GET /api/monitoring/filters`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 描述：获取系统中所有已存在的学院、年级、班级列表，用于前端下拉筛选框
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "colleges": ["计算机学院", "通信工程学院", "艺术学院"],
    "grades": ["2023级", "2022级", "2021级", "2020级"],
    "clazzes": ["计科2301", "计科2302", "软工2301"]
  }
}
```
- 示例：
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/monitoring/filters"
```

### 获取监控概览数据（支持筛选）
- `GET /api/monitoring/overview`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 查询参数：
  - `college`：学院名称（可选）
  - `grade`：年级（可选）
  - `clazz`：班级（可选）
- 描述：根据筛选条件返回统计卡片所需的核心指标
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalUsers": 120,              // 筛选条件下的总人数
    "totalDuration": 1500.5,        // 总志愿时长 (小时)
    "averageDuration": 12.5,        // 平均志愿时长
    "totalActivities": 300,         // 总参加活动人次
    "averageActivities": 2.5,       // 平均参加活动数
    "completedActivities": 280      // 已完成的活动数
  }
}
```
- 示例：
```bash
# 查询计算机学院2023级的统计数据
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/monitoring/overview?college=计算机学院&grade=2023级"
```

### 获取用户详细统计列表（分页）
- `GET /api/monitoring/user-stats`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 查询参数：
  - `page`：页码（默认 1）
  - `pageSize`：每页大小（默认 10）
  - `college`：学院（可选）
  - `grade`：年级（可选）
  - `clazz`：班级（可选）
  - `sortField`：排序字段（可选：duration/totalDuration, activityCount）
  - `sortOrder`：排序方式（可选：asc, desc，默认 desc）
- 描述：分页获取用户的详细统计数据，支持筛选和排序
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 120,
    "current": 1,
    "size": 10,
    "records": [
      {
        "studentNo": "20230001",
        "name": "张三",
        "college": "计算机学院",
        "grade": "2023级",
        "clazz": "计科2301班",
        "totalDuration": 45.5,     // 个人总时长
        "activityCount": 5,        // 个人参加活动数
        "rank": 1                  // 排名
      },
      {
        "studentNo": "20230002",
        "name": "李四",
        "college": "计算机学院",
        "grade": "2023级",
        "clazz": "计科2301班",
        "totalDuration": 38.0,
        "activityCount": 4,
        "rank": 2
      }
    ]
  }
}
```
- 示例：
```bash
# 查询计算机学院学生，按志愿时长降序排列
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/monitoring/user-stats?college=计算机学院&sortField=duration&sortOrder=desc&page=1&pageSize=20"

# 查询2023级所有学生，按活动参与数排序
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/monitoring/user-stats?grade=2023级&sortField=activityCount&sortOrder=desc"
```

### 总览监控
- `GET /api/monitoring/overview`
- 行为：返回系统与业务概览指标，并包含告警统计与最近告警
- 响应示例结构：
```
{
  "cpu": { "usage": 0.23 },
  "memory": { "used": 123456789 },
  "alerts": {
    "statistics": { /* 告警统计 */ },
    "recentAlerts": [ /* 最近告警列表 */ ]
  }
}
```

- `GET /api/monitoring/system`
- 行为：返回系统指标（CPU、内存、线程等）

- `GET /api/monitoring/rabbitmq`
- 行为：返回队列状态与指标

- `GET /api/monitoring/business`
- 行为：返回业务指标（活动数、报名数、志愿时长等）

- `GET /api/monitoring/metrics`
- 行为：返回所有监控指标的汇总

### 告警列表
- `GET /api/monitoring/alerts?limit=<N>`
- 查询参数：`limit`（默认 20）
- 响应示例结构：
```
{
  "alerts": [ /* 告警列表 */ ],
  "statistics": { /* 告警统计 */ }
}
```

### 操作日志
- `GET /api/monitoring/logs`
- 查询参数：
  - `studentNo`（可选）
  - `operation`（可选）
  - `startTime`、`endTime`（可选，ISO DateTime）
  - `page`（默认 `1`）、`pageSize`（默认 `20`）
- 响应：
```
{
  "logs": [ /* 操作日志 */ ],
  "total": 123,
  "page": 1,
  "pageSize": 20
}
```

### 健康检查
- `GET /api/monitoring/health`
- 行为：返回服务健康状态与时间戳
- 响应示例结构：
```
{
  "status": "UP",
  "rabbitmq": "UP",
  "timestamp": 1732612345678
}
```

## 活动导入（Activity Import）

### 导入已完成的活动
- `POST /api/activities/import`
- Content-Type: `multipart/form-data`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 表单字段：
  - `name`（必需）：活动名称
  - `type`（必需）：活动类型（ActivityType枚举值）
  - `description`：活动描述
  - `duration`（必需）：活动时长（小时）
  - `endTime`（必需）：活动结束时间（ISO-8601 OffsetDateTime）
  - `functionary`（必需）：活动负责人学号
  - `participants[]`：参与者学号数组（可选）
  - `file`：Excel文件，包含参与者学号列表（第一列为学号，第一行为表头将被跳过）
  - `coverFile`：封面图片文件（可选）
  - `attachment[]`：附件路径数组（可选）
- 行为：
  - **管理员（admin/superAdmin）**：直接创建已完成的活动（status=ActivityEnded, imported=true），立即生效
  - **负责人（functionary）**：创建待审核的活动记录，需要管理员审核后才会生效
  - 参与者来源：`participants[]` 数组 + Excel文件解析结果（合并去重）
  - 所有参与者必须在系统中存在，否则返回错误
  - 导入的活动所有时间字段（报名开始/结束、活动开始/结束）都设置为endTime
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "活动ID",
    "status": "APPROVED" // 管理员导入
    // 或
    "status": "PENDING_REVIEW" // 负责人导入
  }
}
```
- 示例：
```bash
# 管理员导入（直接生效）
curl -X POST -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -F "name=社区志愿服务" \
  -F "type=COMMUNITY_SERVICE" \
  -F "description=街道清洁活动" \
  -F "duration=3.5" \
  -F "endTime=2025-12-10T16:00:00+08:00" \
  -F "functionary=20230001" \
  -F "participants[]=20230002" \
  -F "participants[]=20230003" \
  -F "file=@/path/to/participants.xlsx" \
  -F "coverFile=@/path/to/cover.jpg" \
  http://localhost:8080/api/activities/import

# 负责人导入（需要审核）
curl -X POST -H "Authorization: Bearer <FUNCTIONARY_TOKEN>" \
  -F "name=社区志愿服务" \
  -F "type=COMMUNITY_SERVICE" \
  -F "duration=3.5" \
  -F "endTime=2025-12-10T16:00:00+08:00" \
  -F "functionary=20230001" \
  -F "file=@/path/to/participants.xlsx" \
  http://localhost:8080/api/activities/import
```

## 待审核活动（Pending Activities）

### 列出待审核活动
- `GET /api/pending-activities`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 查询参数：
  - `page`（默认 `1`）、`pageSize`（默认 `10`）
  - `type`（ActivityType 枚举名）
  - `functionary`：活动负责人学号
  - `name`：活动名称（支持模糊匹配）
  - `submittedBy`：提交者学号
- 权限：
  - **管理员（admin/superAdmin）**：查看所有待审核活动
  - **负责人（functionary）**：只能查看自己提交的待审核活动
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "pending-activity-id",
        "functionary": "20230001",
        "name": "社区志愿服务",
        "type": "COMMUNITY_SERVICE",
        "description": "街道清洁活动",
        "duration": 3.5,
        "endTime": "2025-12-10T16:00:00+08:00",
        "coverPath": "/covers/xxx.jpg",
        "coverImage": "data:image/jpeg;base64,...",
        "createdAt": "2025-12-15T10:30:00+08:00",
        "submittedBy": "20230001",
        "attachment": ["/files/a.pdf"],
        "participants": ["20230002", "20230003"]
      }
    ],
    "total": 5,
    "page": 1,
    "pageSize": 10
  }
}
```

### 获取待审核活动详情
- `GET /api/pending-activities/{id}`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 响应：待审核活动详情（同上述结构中的单个item）

### 审核通过待审核活动
- `POST /api/pending-activities/{id}/approve`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需；仅管理员）
- 行为：将待审核活动转换为正式活动，状态设置为 ActivityEnded，imported=true
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "activityId": "已创建的正式活动ID"
  }
}
```

### 拒绝待审核活动
- `POST /api/pending-activities/{id}/reject?reason=<拒绝理由>`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需；仅管理员）
- 查询参数：
  - `reason`（可选）：拒绝理由
- 行为：删除待审核活动及其相关数据（参与者、附件、封面图片）
- 响应：
```json
{
  "code": 200,
  "message": "success"
}
```

### 删除待审核活动
- `DELETE /api/pending-activities/{id}`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 权限：提交者本人或管理员
- 行为：删除待审核活动及其相关数据
- 响应：
```json
{
  "code": 200,
  "message": "success"
}
```

## 个人志愿时长申请（Personal Hour Request）

### 数据结构
#### PersonalHourRequest
```json
{
  "id": "string",
  "applicantStudentNo": "string",     // 申请人学号
  "applicantName": "string",          // 申请人姓名
  "name": "string",                   // 活动名称
  "functionary": "string",            // 证明人/负责人姓名
  "type": "ActivityType",             // 活动类型
  "description": "string",            // 活动简述
  "startTime": "OffsetDateTime",      // 活动开始时间
  "endTime": "OffsetDateTime",        // 活动结束时间
  "duration": 2.5,                    // 申请时长（小时）
  "status": "RequestStatus",          // 审核状态
  "rejectedReason": "string",         // 拒绝理由（仅当status=REJECTED时有值）
  "createdAt": "OffsetDateTime",      // 申请时间
  "reviewedAt": "OffsetDateTime",     // 审核时间
  "reviewedBy": "string",             // 审核人学号
  "attachments": ["string"]           // 证明材料路径列表
}
```

#### RequestStatus（申请状态枚举）
- `PENDING`：审核中
- `APPROVED`：已通过
- `REJECTED`：已拒绝

### 提交个人时长申请
- `POST /api/activities/request_hours`
- Content-Type: `multipart/form-data`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 表单字段：
  - `name`（必需）：活动名称
  - `functionary`（必需）：证明人/负责人姓名
  - `type`（必需）：活动类型（ActivityType枚举值）
  - `description`：活动简述
  - `startTime`（必需）：活动开始时间（ISO-8601 OffsetDateTime）
  - `endTime`（必需）：活动结束时间（ISO-8601 OffsetDateTime）
  - `duration`（必需）：申请时长（小时）
  - `files`：证明材料文件（支持多个图片/文档）
- 行为：创建一个待审核的个人时长申请
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "申请已提交，等待审核",
    "data": { /* PersonalHourRequest */ }
  }
}
```
- 示例：
```bash
curl -X POST -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "name=社区志愿服务" \
  -F "functionary=张老师" \
  -F "type=COMMUNITY_SERVICE" \
  -F "description=参与街道清洁活动" \
  -F "startTime=2025-12-10T09:00:00+08:00" \
  -F "endTime=2025-12-10T12:00:00+08:00" \
  -F "duration=3" \
  -F "files=@/path/to/proof1.jpg" \
  -F "files=@/path/to/proof2.jpg" \
  http://localhost:8080/api/activities/request_hours
```

### 获取待审核申请列表（管理员）
- `GET /api/activities/pending_requests`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需；仅admin/superAdmin可访问）
- 查询参数：
  - `page`（默认 `1`）、`pageSize`（默认 `10`）
- 行为：获取所有状态为PENDING的个人时长申请
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [ /* PersonalHourRequest[] */ ],
    "total": 15,
    "page": 1,
    "pageSize": 10
  }
}
```
- 失败响应：
  - 未认证：`{"code":401, "message":"UNAUTHORIZED"}`
  - 无权限：`{"code":403, "message":"FORBIDDEN"}`
- 示例：
```bash
curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://localhost:8080/api/activities/pending_requests?page=1&pageSize=10"
```

### 审核申请
- `POST /api/activities/review_request/{id}`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需；仅admin/superAdmin可访问）
- 查询参数：
  - `approved`（必需）：是否通过（true/false）
  - `reason`（拒绝时必需）：拒绝理由
- 行为：
  - 通过（approved=true）：更新状态为APPROVED，将申请的时长添加到申请人的总时长
  - 拒绝（approved=false）：更新状态为REJECTED，记录拒绝理由
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "data": { /* 更新后的 PersonalHourRequest */ }
  }
}
```
- 失败响应：
  - 未找到：`{"code":404, "message":"NOT_FOUND"}`
  - 已审核：`{"code":400, "message":"ALREADY_REVIEWED"}`
  - 拒绝时未填写理由：`{"code":400, "message":"REASON_REQUIRED"}`
- 示例：
```bash
# 通过申请
curl -X POST -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://localhost:8080/api/activities/review_request/{id}?approved=true"

# 拒绝申请
curl -X POST -H "Authorization: Bearer <ADMIN_TOKEN>" \
  "http://localhost:8080/api/activities/review_request/{id}?approved=false&reason=证明材料不足"
```

### 获取我的申请记录
- `GET /api/activities/my_requests`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 查询参数：
  - `page`（默认 `1`）、`pageSize`（默认 `10`）
  - `status`（可选）：筛选状态（PENDING/APPROVED/REJECTED）
- 行为：获取当前登录用户的所有申请记录
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [ /* PersonalHourRequest[] */ ],
    "total": 5,
    "page": 1,
    "pageSize": 10
  }
}
```
- 示例：
```bash
# 获取所有申请
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/activities/my_requests"

# 只获取审核中的申请
curl -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/activities/my_requests?status=PENDING"
```

### 获取申请详情
- `GET /api/activities/request/{id}`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 权限：申请人本人或管理员
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": { /* PersonalHourRequest */ }
}
```
- 失败响应：
  - 未找到：`{"code":404, "message":"NOT_FOUND"}`
  - 无权限：`{"code":403, "message":"FORBIDDEN"}`

### 撤销申请
- `DELETE /api/activities/request/{id}`
- 请求头：`Authorization: Bearer <JWT_TOKEN>`（必需）
- 权限：仅申请人本人，且申请状态为PENDING
- 行为：删除待审核的申请及其附件
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "申请已撤销"
  }
}
```
- 失败响应：
  - 未找到：`{"code":404, "message":"NOT_FOUND"}`
  - 无权限：`{"code":403, "message":"FORBIDDEN"}`
  - 已审核无法撤销：`{"code":400, "message":"CANNOT_DELETE_REVIEWED"}`
- 示例：
```bash
curl -X DELETE -H "Authorization: Bearer <JWT_TOKEN>" \
  "http://localhost:8080/api/activities/request/{id}"
```

## 用户管理（Users）补充

### 列出所有用户
- `GET /user/listAll`
- 行为：返回所有用户列表（用于导入活动时选择负责人和参与者）
- 响应：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "studentNo": "20230001",
      "username": "张三",
      "role": "user",
      "totalHours": 10.5
    },
    {
      "studentNo": "20230002",
      "username": "李四",
      "role": "functionary",
      "totalHours": 25.0
    }
  ]
}
```

## Activity 数据结构更新

在原有Activity结构基础上，新增以下字段：
- `rejectedReason`（string, 可选）：活动被拒绝的理由（status=FailReview时有值）
- `imported`（boolean）：是否为导入的活动（true表示导入的已完成活动）

审核活动接口更新：
- `POST /api/activities/{id}/review?approve=<true|false>&reason=<拒绝理由>`
- 新增 `reason` 参数（可选）：当 `approve=false` 时可提供拒绝理由

## Excel 文件格式说明

导入活动时上传的Excel文件格式要求：
- 文件格式：`.xlsx`（Excel 2007+）
- 第一列：学号（student_no）
- 第一行：表头（将被跳过）
- 示例：

| 学号 |
|------|
| 20230001 |
| 20230002 |
| 20230003 |

系统会自动解析第一列的所有学号（跳过表头行），并与 `participants[]` 参数合并去重。

