# 志愿时长管理系统 API 文档

## 基础信息

- 基础路径: `/api`
- 认证方式: JWT Token (Header: `Authorization: Bearer <token>`)

---

## 用户认证与管理模块

### 用户登录
- **URL**: `GET /api/user/login`
- **描述**: 用户登录获取token
- **需要认证**: 否
- **查询参数**:
  - `studentNo`: 学号（必需）
  - `password`: 密码（必需）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "jwt_token",
    "studentNo": "学号",
    "username": "用户名",
    "role": "角色"
  }
}
```

### 验证Token
- **URL**: `GET /api/user/verifyToken`
- **描述**: 验证JWT Token是否有效
- **需要认证**: 是
- **响应**:
```json
{
  "code": 200,
  "message": "Token有效",
  "data": null
}
```

### 获取当前用户信息
- **URL**: `GET /api/user/getUser`
- **描述**: 获取当前登录用户信息
- **需要认证**: 是
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "studentNo": "学号",
    "username": "用户名",
    "name": "姓名",
    "role": "角色",
    "totalHours": 10.5
  }
}
```

### 根据学号获取用户
- **URL**: `GET /api/user/getUserByStudentNo`
- **描述**: 根据学号获取用户信息
- **需要认证**: 是
- **查询参数**:
  - `studentNo`: 学号（必需）

### 获取所有用户列表
- **URL**: `GET /api/user/listAll`
- **描述**: 获取所有用户列表
- **需要认证**: 是

---

## 活动管理模块

### 查询活动列表（推荐）
- **URL**: `POST /api/activities/query`
- **描述**: 查询活动列表（分页、筛选、排序）
- **需要认证**: 是
- **请求体**:
```json
{
  "page": 1,
  "pageSize": 10,
  "type": "活动类型（可选）",
  "status": "活动状态（可选）",
  "functionary": "负责人学号（可选）",
  "name": "活动名称（可选）",
  "startFrom": "开始时间起（ISO 8601格式，可选）",
  "startTo": "开始时间止（ISO 8601格式，可选）",
  "isFull": false
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

### 获取活动详情
- **URL**: `GET /api/activities/{id}`
- **描述**: 获取活动详情
- **需要认证**: 是

### 创建活动
- **URL**: `POST /api/activities`
- **描述**: 创建新活动
- **需要认证**: 是
- **Content-Type**: `multipart/form-data`
- **请求参数**: 活动信息（multipart form）

### 更新活动
- **URL**: `PUT /api/activities/{id}`
- **描述**: 更新活动信息
- **需要认证**: 是
- **权限**: 负责人或管理员
- **Content-Type**: `multipart/form-data`

### 删除活动
- **URL**: `DELETE /api/activities/{id}`
- **描述**: 删除活动
- **需要认证**: 是
- **权限**: ADMIN

### 刷新活动状态
- **URL**: `POST /api/activities/refreshStatuses`
- **描述**: 刷新所有活动状态（根据时间自动更新）
- **需要认证**: 是
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "updated": 5
  }
}
```

### 报名参加活动
- **URL**: `POST /api/activities/{id}/enroll`
- **描述**: 报名参加活动
- **需要认证**: 是
- **响应状态码**:
  - `200`: 报名成功
  - `404`: 活动不存在
  - `409`: 名额已满/已报名

### 取消报名
- **URL**: `POST /api/activities/{id}/unenroll`
- **描述**: 取消活动报名
- **需要认证**: 是
- **响应状态码**:
  - `200`: 取消成功
  - `404`: 活动不存在
  - `403`: 负责人不能取消报名
  - `400`: 报名已结束
  - `409`: 未报名

### 审核活动
- **URL**: `POST /api/activities/{id}/review`
- **描述**: 审核活动（管理员）
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `approve`: true/false（必需）
  - `reason`: 拒绝原因（拒绝时可选）

### 获取我的活动
- **URL**: `GET /api/activities/MyActivities`
- **描述**: 获取当前用户负责的活动
- **需要认证**: 是
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）

### 获取我的参与状态
- **URL**: `GET /api/activities/MyStatus`
- **描述**: 获取当前用户的参与活动统计
- **需要认证**: 是
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalDuration": 20.5,
    "totalActivities": 8,
    "activities": []
  }
}
```

### 导入活动（非批量）
- **URL**: `POST /api/activities/import`
- **描述**: 通过Excel文件导入单个活动及参与者
- **需要认证**: 是
- **Content-Type**: `multipart/form-data`
- **请求参数**:
  - `name`: 活动名称（必需）
  - `type`: 活动类型（必需）
  - `description`: 活动描述（可选）
  - `duration`: 默认志愿时长（必需）
  - `endTime`: 活动结束时间（必需）
  - `functionary`: 负责人学号（必需）
  - `file`: Excel文件（可选）
  - `coverFile`: 封面图片（可选）
  - `participants[]`: 手动添加的参与者学号数组（可选）
  - `attachment[]`: 附件（可选）
- **处理逻辑**:
  - 管理员/负责人：直接导入，立即生效
  - 普通用户：提交审核，需管理员审核通过后才生效

#### Excel 文件格式（非批量导入）

| 列 | 内容 | 说明 |
|---|---|---|
| 第一列 | 学号 | 必需 |
| 第二列 | 志愿时长 | 可选 |

**时长处理规则：**
- 如果第二列为空、null或-1：使用前端传入的默认 `duration` 值
- 如果第二列有有效数值且不为-1：使用Excel中的时长值（以Excel为准）

**示例：**

| 学号 | 志愿时长 |
|------|----------|
| 20230001 | 3.5 |
| 20230002 | |
| 20230003 | -1 |
| 20230004 | 2 |

假设前端传入 `duration=4`：
- 20230001：使用Excel时长 **3.5** 小时
- 20230002：使用默认时长 **4** 小时（第二列为空）
- 20230003：使用默认时长 **4** 小时（第二列为-1）
- 20230004：使用Excel时长 **2** 小时

### 上传活动附件
- **URL**: `POST /api/activities/upload/attachment`
- **描述**: 上传活动附件（文档、图片、压缩包等）
- **需要认证**: 是
- **Content-Type**: `multipart/form-data`
- **请求参数**:
  - `file`: 文件（必需）
  - `description`: 文件描述（可选）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileName": "原始文件名.pdf",
    "filePath": "/attachments/uuid_filename.pdf",
    "fileType": "pdf",
    "fileSize": 1024000,
    "description": "文件描述"
  }
}
```

### 删除活动附件
- **URL**: `DELETE /api/activities/attachment`
- **描述**: 删除活动附件
- **需要认证**: 是
- **查询参数**:
  - `filePath`: 文件路径（必需）

### 获取附件信息
- **URL**: `GET /api/activities/attachment/info`
- **描述**: 获取附件信息
- **需要认证**: 是
- **查询参数**:
  - `filePath`: 文件路径（必需）

---

## 待审核活动模块

### 查询待审核活动列表（推荐）
- **URL**: `POST /api/pending-activities/query`
- **描述**: 查询待审核活动列表（分页、筛选）
- **需要认证**: 是
- **请求体**:
```json
{
  "page": 1,
  "pageSize": 10,
  "type": "活动类型（可选）",
  "functionary": "负责人学号（可选）",
  "name": "活动名称（可选）",
  "submittedBy": "提交人学号（可选）"
}
```
- **注意**: 负责人角色只能查看自己提交的待审核活动

### 获取待审核活动列表（旧接口）
- **URL**: `GET /api/pending-activities`
- **描述**: 获取待审核活动列表
- **需要认证**: 是
- **权限**: ADMIN（负责人只能看自己提交的）
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
  - `type`: 活动类型（可选）
  - `functionary`: 负责人学号（可选）
  - `name`: 活动名称（可选）
  - `submittedBy`: 提交人学号（可选）
- **已废弃**: 推荐使用 POST /api/pending-activities/query

### 获取待审核活动详情
- **URL**: `GET /api/pending-activities/{id}`
- **描述**: 获取待审核活动详情
- **需要认证**: 是

### 审核通过
- **URL**: `POST /api/pending-activities/{id}/approve`
- **描述**: 审核通过待审核活动
- **需要认证**: 是
- **权限**: ADMIN
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "activityId": "新创建的活动ID"
  }
}
```

### 审核拒绝
- **URL**: `POST /api/pending-activities/{id}/reject`
- **描述**: 审核拒绝待审核活动
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `reason`: 拒绝原因（可选）

### 删除待审核活动
- **URL**: `DELETE /api/pending-activities/{id}`
- **描述**: 删除待审核活动
- **需要认证**: 是
- **权限**: 提交者或管理员

---

## 批量导入模块

### 批量导入活动
- **URL**: `POST /api/pending-activities/batch-import`
- **描述**: 批量导入多个活动及用户数据
- **需要认证**: 是
- **Content-Type**: `multipart/form-data`
- **请求参数**:
  - `file`: Excel文件（必需，支持.xlsx和.xls格式）

#### Excel 文件格式（批量导入）

| 列序号 | 列名 | 说明 |
|-------|------|------|
| 1 | 姓名 | 用户姓名 |
| 2 | 性别 | 用户性别 |
| 3 | 学院 | 用户学院 |
| 4 | 年级 | 用户年级 |
| 5 | 学号 | 用户学号（必需） |
| 6 | 联系方式 | 用户电话 |
| 7 | 服务时长（小时） | 志愿服务时长 |
| 8 | 活动名称 | 活动名称（必需） |

#### 处理逻辑
- **用户处理**：学号存在则直接使用，不存在则创建新用户（默认密码为学号）
- **活动名称去重**：活动名称后的小时数标记会被去除（如"xxx(2小时)"和"xxx(3小时)"视为同一活动"xxx"）
- **活动处理**：活动存在则加入，不存在则创建（标记imported=true，状态为已结束）
- **审核流程**：
  - 管理员/负责人：直接导入，立即生效
  - 普通用户：提交审核，需管理员审核通过后才生效

#### 响应示例
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "batchId": "uuid（提交审核时返回）",
    "totalRecords": 10,
    "newUsersCreated": 3,
    "newActivitiesCreated": 2,
    "participantsAdded": 10,
    "hoursGranted": 10,
    "createdUserStudentNos": ["20230001", "20230002"],
    "createdActivityNames": ["社区服务", "文化宣传"],
    "errors": [],
    "status": "APPROVED 或 PENDING_REVIEW"
  }
}
```

### 获取待审核批量导入列表
- **URL**: `GET /api/pending-activities/batch-import`
- **描述**: 获取待审核批量导入列表（分页）
- **需要认证**: 是
- **查询参数**:
  - `status`: 状态筛选（PENDING/APPROVED/REJECTED）
  - `submittedBy`: 提交人学号筛选（管理员可用）
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）

### 获取待审核批量导入详情
- **URL**: `GET /api/pending-activities/batch-import/{batchId}`
- **描述**: 获取指定批次的详细记录
- **需要认证**: 是

### 审核通过批量导入
- **URL**: `POST /api/pending-activities/batch-import/{batchId}/approve`
- **描述**: 审核通过批量导入
- **需要认证**: 是
- **权限**: ADMIN

### 审核拒绝批量导入
- **URL**: `POST /api/pending-activities/batch-import/{batchId}/reject`
- **描述**: 审核拒绝批量导入
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `reason`: 拒绝原因（可选）

### 删除待审核批量导入
- **URL**: `DELETE /api/pending-activities/batch-import/{batchId}`
- **描述**: 删除待审核批量导入记录
- **需要认证**: 是
- **权限**: 管理员可删除任意，普通用户只能删除自己提交的PENDING状态记录

---

## 文件下载与预览模块

### 下载文件
- **URL**: `GET /api/files/download`
- **描述**: 下载文件（附件、封面等）
- **需要认证**: 是
- **查询参数**:
  - `path`: 文件相对路径（必需，例如：/attachments/xxxx.pdf）
- **响应**: 文件流（attachment方式下载）

### 预览文件
- **URL**: `GET /api/files/preview`
- **描述**: 在浏览器中预览文件（inline方式显示）
- **需要认证**: 是
- **查询参数**:
  - `path`: 文件相对路径（必需，例如：/covers/xxxx.png）
- **响应**: 文件流（inline方式显示）

---

## 个人时长申请模块

### 提交时长申请
- **URL**: `POST /api/activities/request_hours`
- **描述**: 用户提交个人志愿时长申请
- **需要认证**: 是
- **Content-Type**: `multipart/form-data`
- **请求参数**:
  - `name`: 活动名称（必需）
  - `functionary`: 负责人学号（必需）
  - `type`: 活动类型（必需）
  - `description`: 活动描述（可选）
  - `startTime`: 开始时间（必需，ISO 8601格式）
  - `endTime`: 结束时间（必需，ISO 8601格式）
  - `duration`: 申请时长（小时）（必需）
  - `files`: 附件文件列表（可选）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "message": "申请已提交，等待审核",
    "data": {}
  }
}
```

### 获取待审核申请列表（管理员）
- **URL**: `GET /api/activities/pending_requests`
- **描述**: 获取待审核的个人时长申请列表
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 10,
    "page": 1,
    "pageSize": 10
  }
}
```

### 审核时长申请（管理员）
- **URL**: `POST /api/activities/review_request/{id}`
- **描述**: 审核个人时长申请
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `approved`: true/false（必需）
  - `reason`: 拒绝原因（拒绝时可选）
- **响应状态码**:
  - `200`: 审核成功
  - `400`: 参数错误或已审核
  - `404`: 申请不存在

### 获取我的申请列表
- **URL**: `GET /api/activities/my_requests`
- **描述**: 获取当前用户提交的时长申请列表
- **需要认证**: 是
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
  - `status`: 状态筛选（可选：PENDING_REVIEW/APPROVED/REJECTED）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 5,
    "page": 1,
    "pageSize": 10
  }
}
```

### 获取申请详情
- **URL**: `GET /api/activities/request/{id}`
- **描述**: 获取指定申请的详情
- **需要认证**: 是
- **权限**: 申请人或管理员
- **响应状态码**:
  - `200`: 成功
  - `403`: 无权查看
  - `404`: 申请不存在

### 删除申请
- **URL**: `DELETE /api/activities/request/{id}`
- **描述**: 删除个人时长申请（仅申请人可删除待审核状态的申请）
- **需要认证**: 是
- **权限**: 申请人
- **响应状态码**:
  - `200`: 删除成功
  - `400`: 已审核的申请不能删除
  - `403`: 无权删除
  - `404`: 申请不存在

---

## 意见建议模块

### 创建意见建议
- **URL**: `POST /api/suggestions`
- **描述**: 用户提交意见建议
- **需要认证**: 是
- **请求体**:
```json
{
  "title": "建议标题",
  "content": "建议内容"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "建议ID",
    "title": "建议标题",
    "content": "建议内容",
    "status": "PENDING",
    "submittedBy": "提交人学号",
    "submittedAt": "2024-01-01T00:00:00Z"
  }
}
```

### 获取我的建议列表
- **URL**: `GET /api/suggestions/my`
- **描述**: 获取当前用户提交的建议列表
- **需要认证**: 是
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 5
  }
}
```

### 获取所有建议列表（管理员）
- **URL**: `GET /api/suggestions`
- **描述**: 获取所有建议列表
- **需要认证**: 是
- **权限**: ADMIN
- **查询参数**:
  - `page`: 页码（默认1）
  - `pageSize`: 每页数量（默认10）
  - `status`: 状态筛选（可选：PENDING/REPLIED）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 20
  }
}
```

### 回复建议（管理员）
- **URL**: `POST /api/suggestions/{id}/reply`
- **描述**: 管理员回复建议
- **需要认证**: 是
- **权限**: ADMIN
- **请求体**:
```json
{
  "replyContent": "回复内容"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "建议ID",
    "status": "REPLIED",
    "replyContent": "回复内容",
    "repliedAt": "2024-01-01T12:00:00Z"
  }
}
```

---

## 监控大屏模块

### 获取监控大屏数据
- **URL**: `GET /api/monitoring/dashboard`
- **描述**: 获取监控大屏主数据
- **需要认证**: 是
- **权限**: SUPER_ADMIN
- **查询参数**:
  - `timeRange`: 时间范围（默认monthly，可选：daily/weekly/monthly/yearly）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalUsers": 1000,
    "totalActivities": 50,
    "totalHours": 5000.5,
    "averageHoursPerUser": 5.0,
    "activityTypeDistribution": {},
    "hoursTrend": [],
    "topColleges": [],
    "recentActivities": []
  }
}
```

### 获取筛选选项
- **URL**: `GET /api/monitoring/filters`
- **描述**: 获取监控筛选选项（学院、年级、班级列表）
- **需要认证**: 是
- **权限**: SUPER_ADMIN
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "colleges": ["学院1", "学院2"],
    "grades": ["2021", "2022"],
    "classes": ["班级1", "班级2"]
  }
}
```

### 获取概览数据
- **URL**: `GET /api/monitoring/overview`
- **描述**: 获取指定条件下的概览数据
- **需要认证**: 是
- **权限**: SUPER_ADMIN
- **查询参数**:
  - `college`: 学院（可选）
  - `grade`: 年级（可选）
  - `clazz`: 班级（可选）
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalUsers": 100,
    "totalHours": 500.5,
    "averageHours": 5.0,
    "maxHours": 20.0,
    "minHours": 0.0
  }
}
```

### 获取用户统计详情（分页）
- **URL**: `POST /api/monitoring/user-stats`
- **描述**: 获取用户统计详情列表（支持分页、筛选、排序）
- **需要认证**: 是
- **权限**: SUPER_ADMIN
- **请求体**:
```json
{
  "page": 1,
  "pageSize": 10,
  "college": "学院名称（可选）",
  "grade": "年级（可选）",
  "clazz": "班级（可选）",
  "sortField": "排序字段（可选：totalHours/activityCount）",
  "sortOrder": "排序顺序（可选：asc/desc，默认desc）"
}
```
- **响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "studentNo": "学号",
        "name": "姓名",
        "college": "学院",
        "grade": "年级",
        "clazz": "班级",
        "totalHours": 10.5,
        "activityCount": 5
      }
    ],
    "total": 100,
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 响应状态码

| 状态码 | 描述 |
|-------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## 通用响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

## 错误响应格式

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```
