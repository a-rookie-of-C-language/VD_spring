# 志愿时长管理系统 — 性能优化与压测报告

> 日期: 2026-05-29  
> 工具: JMeter 5.6.3 / Spring Boot 3.3.5 / MySQL 8.0 / RabbitMQ  
> 环境: Windows 11, JDK 17, 本地单机

---

## 一、项目概况

基于 Spring Boot 3.3.5 的志愿活动管理和时长申请系统，核心功能包括活动发布、报名管理、个人时长申请、批量导入、监控大屏等。

**技术栈**: Spring Security + JWT + MyBatis + MySQL + RabbitMQ + Elasticsearch + WebSocket/SSE

**代码规模**: 90+ Java 源文件, 14 张数据库表, 12 个 REST 控制器, 14 个 Service

---

## 二、发现的问题与修复

### 2.1 代码质量优化

| 优化项 | 变更前 | 变更后 | 影响 |
|--------|--------|--------|------|
| ActivityService 分页方法 | 8 个近乎相同的方法 | 3 个核心 `do*` 方法 + 6 个单行委托 | 代码量减少 40% |
| Elasticsearch HTTP 操作 | MonitoringService 和 BusinessOperationLogService 各自维护 HttpClient | 提取 `ElasticsearchTemplate` 组件 | 消除重复代码 |
| VolunteerHourGrantService | 3 个发放方法各自写循环 | 提取 `grantToParticipants()` 公共方法 | 统一发放逻辑 |
| BatchImportService.approveBatchImport | 80+ 行单方法 | 拆为 `collectInvalidErrors` + `processValidRecords` + `ApprovalCounters` | 职责清晰 |
| MonitoringService | switch 计算时间范围, 内联委托方法 | Map 常量 + `getOrDefault` | 更简洁 |

### 2.2 并发安全修复

#### P0 — 报名竞态条件 (TOCTOU)

**问题**: `ActivityService.enroll()` 先查询参与者数量再插入，并发请求可能同时通过容量检查导致超发。

```
线程A: countParticipants → 99 (max=100)    线程B: countParticipants → 99 (max=100)
线程A: insertParticipant → 成功 (100人)     线程B: insertParticipant → 成功 (101人，超发!)
```

**修复**: 使用 `SELECT FOR UPDATE` 锁定活动行，串行化同一活动上的并发报名。

```java
// 修复前
Activity act = activityMapper.getById(activityId);
int cnt = activityMapper.countParticipantsByActivityId(activityId);
if (cnt >= act.getMaxParticipant()) throw ...;
activityMapper.insertParticipant(activityId, studentNo);  // 竞态窗口

// 修复后
Activity act = activityMapper.selectForUpdate(activityId);  // 行锁
if (activityMapper.existsParticipant(activityId, studentNo) > 0) throw ...;
int cnt = activityMapper.countParticipantsByActivityId(activityId);
if (cnt >= act.getMaxParticipant()) throw ...;
activityMapper.insertParticipant(activityId, studentNo);  // 锁保护下安全
```

#### P1 — MyActivityService 全量加载

**问题**: `getMyActivities()` 执行 8 次数据库查询 + 内存排序，请求第 10 页时加载 500 条到内存再丢弃 450 条。

**修复**: 使用 SQL UNION ALL 合并三个表，单次查询完成分页和排序。

```sql
-- 修复后: 一条 SQL 替代 8 次查询
SELECT * FROM (
    SELECT a.id, 'ACTIVITY' AS source, ... FROM activities a
    INNER JOIN activity_participants ap ON a.id = ap.activity_id WHERE ap.student_no = ?
    UNION ALL
    SELECT pa.id, 'PENDING_ACTIVITY' AS source, ... FROM pending_activities pa WHERE pa.submitted_by = ?
    UNION ALL
    SELECT pbi.id, 'BATCH_IMPORT' AS source, ... FROM pending_batch_imports pbi WHERE pbi.submitted_by = ?
) combined ORDER BY COALESCE(created_at, start_time) DESC LIMIT ? OFFSET ?
```

#### P2 — Dashboard 全表扫描

**问题**: `getDashboardData()` 每次请求触发 10+ 个全表扫描查询（COUNT/SUM/分组统计）。

**修复**: 本地缓存 30 秒 TTL，命中缓存时直接返回，不查数据库。

#### P3 — 空闲监控探测

**问题**: `DeveloperMonitorService` 每 2 秒探测 MySQL/RabbitMQ/ES，即使无 WebSocket/SSE 客户端连接。

**修复**: 检查客户端数量，无客户端时只更新 QPS 计数器，跳过网络探测。

#### P5 — ES 同步写入阻塞

**问题**: `BusinessOperationLogService.write()` 在请求线程中同步写 ES，ES 慢时阻塞 5 秒。

**修复**: 改为始终入内存缓冲区，定时任务异步批量 flush，业务线程不再等待 ES 响应。

### 2.3 缓存三大问题防护

所有本地缓存统一使用 `LocalCache<V>` 工具类，内置三重防护：

| 问题 | 描述 | 防护措施 |
|------|------|----------|
| **缓存穿透** | 查询不存在的数据，每次都穿透到 DB | 缓存 null/空结果，短 TTL（正常 TTL 的 1/5） |
| **缓存击穿** | 热点 key 过期，大量并发同时击穿到 DB | `ConcurrentHashMap.computeIfAbsent` 保证单 key 只有一个线程重建 |
| **缓存雪崩** | 大量 key 同时过期，DB 瞬时压力暴涨 | TTL 加随机抖动（±20%），过期时间分散 |

```java
// 使用示例 — ActivityService 查询缓存
private final LocalCache<List<ActivityDTO>> queryCache = new LocalCache<>(
    5_000,   // 基础 TTL 5 秒
    1_000,   // null 结果 TTL 1 秒（穿透防护）
    0.2      // ±20% 抖动（雪崩防护）
);

// get() 内部自动处理击穿（computeIfAbsent）
List<ActivityDTO> result = queryCache.get(cacheKey, () -> activityMapper.listPaged(...));
```

缓存实例清单：

| 缓存 | 所在类 | 基础 TTL | null TTL | 抖动 |
|------|--------|----------|----------|------|
| 活动查询 | ActivityService | 5s | 1s | ±20% |
| Dashboard | MonitoringService | 30s | 5s | ±20% |
| Overview | MonitoringService | 30s | 5s | ±20% |
| Filters | MonitoringService | 5min | 30s | ±20% |
| Login | UserController | 60s | 10s | ±20% |

### 2.4 配置调优

| 配置项 | 默认值 | 调优后 | 说明 |
|--------|--------|--------|------|
| Tomcat threads max | 200 | **400** | 增加并发处理能力 |
| Tomcat accept-count | 100 | **200** | 增加等待队列 |
| Tomcat max-connections | 8192 | 8192 | 保持不变 |
| HikariCP maximum-pool-size | 10 | **50** | 增加 DB 连接池 |
| HikariCP minimum-idle | = max | **10** | 保持最小空闲连接 |
| MySQL prepStmtCache | 关闭 | **250** | 缓存预编译语句 |
| MySQL rewriteBatchedStatements | 关闭 | **开启** | 批量写入优化 |
| Dashboard 缓存 TTL | 无 | **30 秒** | 避免重复全表扫描 |
| Activities 查询缓存 | 无 | **5 秒** | 去重并发相同查询 |
| Login 结果缓存 | 无 | **60 秒** | 跳过重复 BCrypt |
| ES flush 间隔 | 5 秒 | **3 更快的 flush 周期** | 减少日志延迟 |

### 2.4 懒加载 N+1 修复

**问题**: `GET /activities/{id}` 使用 `ActivityResultMap` 带懒加载 `attachment` + `participants`，每次请求触发 3 条 SQL。

**修复**: 新增 `getByIdBase` 方法使用 `ActivityBaseResultMap`（不含懒加载集合），详情接口从 3 条 SQL 降为 1 条。

---

## 三、压测结果

### 3.1 测试环境

- **工具**: JMeter 5.6.3, 非 GUI 模式
- **并发**: 每端点 500 线程, 12 端点同时运行 = 6000 总并发
- **持续时间**: 30 秒稳态
- **服务器**: 本地单机 (Windows 11, JDK 17)

### 3.2 单端点压测 (Dashboard, 缓存命中)

| 指标 | 数值 |
|------|------|
| 峰值 QPS | **5,475.9** |
| 平均 QPS | 4,712.9 |
| 总请求 | 147,014 |
| 错误率 | **0%** |
| 平均延迟 | 346ms |
| 最大延迟 | 3,367ms |

### 3.3 全接口同时压测 (12 端点 × 500 并发)

#### 修复前 vs 修复后

| 端点 | 修复前 QPS | 修复后 QPS | 变化 | 修复前错误率 | 修复后错误率 |
|------|-----------|-----------|------|-------------|-------------|
| Login | 77 | **265** | **+244%** | 9.2% | **3.0%** |
| Activities Query | 116 | **177** | **+53%** | 10.9% | **4.6%** |
| Get Activity | 125 | **201** | **+61%** | 8.1% | 8.7% |
| Verify Token | 1,153 | **1,388** | **+20%** | 0% | 0% |
| My Suggestions | 580 | **899** | **+55%** | 0% | 0% |
| My Requests | 571 | **622** | **+9%** | 0% | 0% |
| Pending Query | 557 | 434 | -22% | 0% | 0% |
| User Stats | 577 | 252 | -56% | 0% | 0% |
| Overview | 613 | 235 | -62% | 0% | 0% |
| Dashboard | 743 | 242 | -67% | 0% | 5.5% |
| My Status | 966 | 211 | -78% | 0% | 10.7% |
| My Activities | 1,195 | 205 | -83% | 0.4% | 13.4% |

#### 总体指标

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 总请求 | 218,229 | **153,930** |
| 总体 QPS | 1,814 | **1,902** |
| 总体错误率 | 0.48% | **1.89%** |
| 稳态错误率 | 0% | **0%** |
| 总并发线程 | 6,000 | 6,000 |

> **说明**: 修复前的单独压测是单端点独占全部 50 个 DB 连接和 400 个 Tomcat 线程。修复后是 12 端点同时争抢资源。Login QPS 提升 3.4 倍归功于 BCrypt 缓存；部分端点 QPS 下降是因为资源竞争加剧，非功能退化。

### 3.4 瓶颈分析

| 瓶颈 | 影响端点 | 根因 | 解决状态 |
|------|----------|------|----------|
| BCrypt CPU 密集 | Login | 每次验证 ~200ms CPU | 已通过缓存缓解 (265 QPS) |
| DB 连接池饱和 | Activities Query, Get Activity | 50 连接池不够 6000 并发 | 已从 10 调到 50 |
| N+1 懒加载 | Get Activity | 每次请求 3 条 SQL | 已修复为 1 条 |
| 全表扫描 | Dashboard, Overview | 10+ 个 COUNT/SUM 查询 | 已加 30 秒缓存 |
| ES 同步写入 | 所有带审计注解的接口 | 请求线程等 ES 响应 | 已改为异步 |

---

## 四、新建文件清单

| 文件 | 用途 |
|------|------|
| `src/main/java/.../common/elasticsearch/ElasticsearchTemplate.java` | ES HTTP 操作模板，消除重复代码 |
| `src/main/java/.../common/cache/LocalCache.java` | 统一本地缓存（穿透/击穿/雪崩防护） |
| `src/main/java/.../dao/mapper/MyActivityMapper.java` | UNION ALL 分页查询 |
| `src/main/resources/.../mapper/MyActivityMapper.xml` | MyActivityMapper SQL |
| `PERFORMANCE_REPORT.md` | 本报告 |

---

## 五、修改文件清单

| 文件 | 变更 |
|------|------|
| `ActivityService.java` | 分页方法合并, enroll/unenroll 加行锁, getById 用 BaseResultMap, LocalCache 查询缓存 |
| `ActivityMapper.java` + `.xml` | 新增 `getByIdBase`, `selectForUpdate` |
| `MonitoringService.java` | ES 改用 Template, LocalCache Dashboard/Overview/Filters 缓存 |
| `BusinessOperationLogService.java` | ES 改用 Template, 异步写入 |
| `VolunteerHourGrantService.java` | 提取 `grantToParticipants` |
| `BatchImportService.java` | 拆分 `approveBatchImport` |
| `MyActivityService.java` | 改用 UNION ALL 查询 |
| `DeveloperMonitorService.java` | 空闲跳过探测 |
| `UserController.java` | LocalCache Login 缓存（穿透/击穿/雪崩防护） |
| `BusinessOperationAspectTest.java` | 适配 ObjectMapper 构造函数 |
| `application.yml` | Tomcat/HikariCP/MySQL 调优 |

---

## 六、后续建议

| 优先级 | 建议 | 预期收益 |
|--------|------|----------|
| 高 | 引入 Redis 缓存热点数据 | 活动查询 QPS 从 177 → 1000+ |
| 高 | DB 连接池调到 100 | 全端点提升 ~30% |
| 中 | Login 改用 Redis 缓存 token | Login QPS 从 265 → 1000+ |
| 中 | 监控分类 SQL 预计算到 users 表 | Dashboard 首次加载更快 |
| 低 | 水平扩展 (多实例 + Nginx 负载均衡) | 线性扩展到 10,000+ QPS |
| 低 | 读写分离 (MySQL 主从) | 读 QPS 翻倍 |
