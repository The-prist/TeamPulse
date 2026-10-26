# TeamPulse 后端开发约定

> 本文件是**强制性**契约。新增模块必须完全遵守，否则会与既有代码风格割裂、并在评审时被视为返工。

## 1. 包结构

```
com.teampulse
├── TeamPulseApplication        启动类（@MapperScan("com.teampulse.**.mapper")）
├── common                      公共基础设施，不含业务
│   ├── api        ApiResponse / PageResult
│   ├── exception  ErrorCode / BizException / GlobalExceptionHandler
│   └── entity     BaseEntity（审计字段 + 逻辑删除）
├── config                      全局配置（Security / MyBatis-Plus / MQTT / 启动初始化）
├── security                    JWT、LoginUser、UserDetails、SecurityUtils
└── module/<领域>               业务模块，每个模块内部自包含
    ├── entity      表实体
    ├── mapper      MyBatis-Plus Mapper
    ├── dto         请求体（含 Bean Validation）
    ├── vo          返回视图对象（需要联表/裁剪字段时才有）
    ├── service     业务实现（**具体类，不写接口**）
    └── controller  REST 接口
```

**不要**为 Service 写 `IXxxService` + `XxxServiceImpl`。项目只有单一实现，接口层是纯噪音；
需要替换实现时用 `@ConditionalOnProperty` 或策略模式即可（参考 `AckPublisherConfig`）。

## 2. 统一响应

所有 Controller 方法返回 `ApiResponse<T>`：

```java
@GetMapping("/list")
public ApiResponse<PageResult<User>> list(...) {
    return ApiResponse.ok(userService.page(...));   // 成功
}
```

- 分页一律返回 `PageResult<T>`（字段 `records/total/page/size`）。
- 需要返回「仅一个 ID」时用 `Map.of("id", id)`，不要为了单个字段新建 DTO。
- 失败**不要**在 Controller 里 try/catch，直接抛 `BizException`，由 `GlobalExceptionHandler` 统一翻译。

## 3. 异常与错误码

```java
throw BizException.of(ErrorCode.NOT_FOUND, "用户不存在: " + id);
```

- 参数格式错误 → `BAD_REQUEST`
- 未登录 → `UNAUTHORIZED`（由 Security 框架抛，业务代码不用管）
- 无权操作 → `FORBIDDEN`
- 资源不存在 → `NOT_FOUND`
- 唯一键冲突 → `CONFLICT`
- 新增错误码必须加到 `ErrorCode` 枚举，并分配未占用码段。

## 4. 鉴权

```java
SecurityUtils.currentUserId();     // 当前登录人 ID（未登录直接 401）
SecurityUtils.currentUser();       // LoginUser（含 role）
SecurityUtils.requireSelfOrAdmin(targetUserId);
```

- 管理端接口加 `@PreAuthorize("hasRole('ADMIN')")`。
- **禁止**从请求参数/请求头读取「当前用户 ID」，必须取自 JWT（`SecurityUtils`）。
- 角色值只有：`admin` / `manager` / `member`。

## 5. 实体与表

- 继承 `BaseEntity` 以获得 `created_at/updated_at/deleted` 自动填充与逻辑删除。
  **例外**：`attendance_record`、`attendance_rule`、`attendance_daily`、`comment`（部分）等
  「只增不改」或已在 DDL 中独立定义的表，可直接写 `@TableName` + 自有时间字段。
- 主键字段名沿用业务名：`user_id`→`userId`、`team_id`→`teamId`、`schedule_id`→`scheduleId`、
  `worklog_id`→`worklogId`、`kanban_id`→`kanbanId`、`column_id`→`columnId`、`item_id`→`itemId`、
  `comment_id`→`commentId`、`word_id`→`wordId`。
- 实体用 Lombok：`@Data @EqualsAndHashCode(callSuper = true) @TableName("xxx")`。
- 敏感字段（密码、设备密钥）用 `@JsonProperty(access = WRITE_ONLY)` 或返回前手动置空。
- 布尔/状态列用 `VARCHAR` 存字符串枚举值，不用 MySQL `ENUM`（改值要 DDL）。

### 表结构（唯一事实来源：`src/main/resources/db/migration/V1__*.sql` / `V2__*.sql`）

| 表 | 关键列 |
|---|---|
| `user` | user_id, username, password, nickname, email, phone, employee_no, role, status, avatar |
| `team` | team_id, team_name, description, creator_id |
| `team_member` | id, team_id, user_id, team_role(leader/member), status, join_time |
| `schedule` | schedule_id, title, content, start_time, end_time, location, status(todo/doing/done/canceled), priority(low/medium/high), creator_id, team_id, remind_time |
| `worklog` | worklog_id, title, content, log_date, creator_id, related_schedule_id, related_team_id |
| `kanban` | kanban_id, kanban_name, description, team_id, creator_id |
| `kanban_column` | column_id, kanban_id, column_name, sort_order |
| `kanban_item` | item_id, kanban_id, schedule_id, column_id, assignee_id, assign_time, sort_order |
| `comment` | comment_id, related_type(schedule/worklog), related_id, creator_id, content, original_content, reply_to |
| `sensitive_word` | word_id, word, replacement, status |
| `iot_device` | id, device_sn, device_name, location, secret, status(ONLINE/OFFLINE/DISABLED), firmware_version, ip_address, last_heartbeat |
| `attendance_rule` | id, rule_name, work_start, work_end, late_grace_minutes, early_grace_minutes, work_days, enabled |
| `attendance_record` | id, user_id, employee_no, device_sn, punch_type, punch_time, work_date, status, late_minutes, early_leave_minutes, source, message_id, sign_verified, raw_payload |
| `attendance_daily` | id, user_id, employee_no, work_date, first_check_in, last_check_out, work_minutes, late_minutes, early_leave_minutes, attendance_status |

## 6. 路由约定

统一前缀 `/api/v1/<模块>`：

| 模块 | 前缀 |
|---|---|
| 认证 | `/api/v1/auth` |
| 用户 | `/api/v1/user` |
| 团队 | `/api/v1/team` |
| 日程 | `/api/v1/schedule` |
| 看板 | `/api/v1/kanban` |
| 日志 | `/api/v1/worklog` |
| 评论 | `/api/v1/comment` |
| 敏感词 | `/api/v1/sensitive-word` |
| 概览 | `/api/v1/dashboard` |
| 考勤 | `/api/v1/attendance` |
| 设备 | `/api/v1/devices` |
| 设备接入 | `/iot/v1`（无需 JWT，走签名） |

REST 风格：
- 列表 `GET /`，分页参数 `page`（默认 1）、`size`（默认 10，上限 200）
- 详情 `GET /{id}`，新增 `POST /`，更新 `PUT /{id}`，删除 `DELETE /{id}`
- 状态流转这类非 CRUD 动作用子路径动词：`POST /{id}/status`、`PUT /{id}/move`

## 7. 参数校验

请求体一律用 `@Valid @RequestBody` + `jakarta.validation` 注解，
错误信息写**中文**，直接面向最终用户：

```java
@Data
public static class CreateRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;
}
```

## 8. 查询与分页

用 MyBatis-Plus Lambda Wrapper，禁止字符串拼 SQL 条件：

```java
var wrapper = Wrappers.<User>lambdaQuery().orderByDesc(User::getUserId);
if (StringUtils.hasText(keyword)) {
    wrapper.and(w -> w.like(User::getUsername, keyword)
                      .or().like(User::getNickname, keyword));
}
IPage<User> page = userMapper.selectPage(new Page<>(p, s), wrapper);
return PageResult.from(page, u -> u);
```

- `LIMIT` 只能通过 `.last("LIMIT n")` 拼接**常量或已校验的整数**。
- 联表查询出现在 Mapper 的 `@Select` 注解里（本项目不使用 XML）。
- 批量补齐关联字段（姓名、设备位置）用 `selectBatchIds` + Map，禁止在循环里查库。

## 9. 事务

- 写多张表的方法加 `@Transactional`（放在 public 方法上）。
- 注意**同类内部调用不会触发事务代理**；需要嵌套事务时把方法拆到另一个 Bean。
- 事务方法内不要吞掉异常后继续写库。

## 10. 注释与命名

- 类级 Javadoc 必须说明「这个类解决什么问题」，而不是复述类名。
- 关键分支（判定口径、幂等、并发、降级）必须有行内注释解释**为什么**。
- 面向用户的字段/枚举值用中文注释标注含义。
- Controller 方法加 `@Operation(summary = "...")`，便于 Swagger 展示。

## 11. 新增模块的自检清单

1. `mvn -q compile` 通过
2. 所有接口返回 `ApiResponse`
3. 管理端接口有 `@PreAuthorize("hasRole('ADMIN')")`
4. 当前用户 ID 来自 `SecurityUtils`，不来自请求参数
5. 列表接口支持分页且 `size` 有上限
6. 写库方法有 `@Transactional`
7. 没有为 Service 创建接口
