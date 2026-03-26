# Monitoring and Audit E2E Checklist

## Preconditions
- Backend app is running on `http://localhost:8080`
- Frontend app is running on `http://localhost:5173`
- Elasticsearch is running and reachable by backend
- Use a `superAdmin` account for monitoring verification

## 1) Developer Monitoring (WebSocket)
1. Login as `superAdmin` in frontend.
2. Open `系统监控（开发者版）` page.
3. Confirm connection badge becomes `WebSocket 已连接`.
4. Confirm cards update over time (`CPU`, `JVM`, `QPS`, `累计请求`).
5. Logout/login with non-superAdmin and verify monitor page access is denied.

## 2) Business Monitoring (Audit Logs)
1. Perform business actions:
   - 负责人发布活动
   - 管理员审核活动（通过/拒绝）
   - 用户提交时长申请，管理员审核
2. Open `系统监控（业务版）`.
3. Verify operation log rows show:
   - 操作人学号（非 anonymous）
   - 操作人角色
   - 动作 + 目标 ID
   - 悬浮显示目标名称
4. Use keyword filter and confirm matched logs are returned.

## 3) Log Center
1. Open `日志中心`.
2. Confirm logs can be loaded.
3. Search by keyword and verify filtered result set.

## 4) Security Regression
1. Open developer monitor in a fresh browser without token.
2. Confirm websocket connection is rejected.
3. Try websocket with a non-superAdmin token and confirm rejection.

## 5) Completion Criteria
- Developer monitoring data is live and stable.
- Business operations appear in ES-backed logs with valid operator identity.
- Monitoring-related pages handle transient API failures with retry and clear error message.
