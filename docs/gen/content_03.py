"""第 3 章：认证与安全。"""


def build(d):
    d.h1("第 3 章  认证与安全")

    d.h2("3.1 原项目的问题：一个可以随手伪造的身份")
    d.p(
        "改造前，后端的「当前用户」是这么拿的：前端登录成功后把 userId 写进 localStorage，"
        "之后每个请求自动带上 `X-User-Id: 3` 这个请求头，后端所有权限判断都读这个头。"
    )
    d.code(
        "// 旧实现（已删除）\n"
        "private boolean isAdmin(HttpServletRequest req) {\n"
        "    String h = req.getHeader(\"X-User-Id\");\n"
        "    Integer uid = Integer.valueOf(h);\n"
        "    User u = userService.getById(uid);\n"
        "    return u != null && \"admin\".equalsIgnoreCase(u.getRole());\n"
        "}",
        caption="任何人在浏览器控制台改一下请求头就能变成管理员",
    )
    d.p("这个设计有四个致命问题，面试时可以作为一个「我发现并修复了什么」的完整案例来讲：")
    d.numbered(
        [
            "**身份完全由客户端声明**：请求头是客户端可控的，等于没有认证。",
            "**没有凭证过期**：localStorage 里的 userId 永久有效，换台电脑复制过去也能用。",
            "**角色存在 sessionStorage**：前端路由守卫据此判断能否进入后台，改一个字符串即可绕过（虽然接口仍有校验，但同样是可伪造的头）。",
            "**每个请求都查一次库**：鉴权逻辑散落在每个 Controller 里，重复且容易漏写。",
        ]
    )

    # ---------------- 3.2 ----------------
    d.h2("3.2 新方案：JWT 无状态认证")
    d.h3("3.2.1 登录与校验流程")
    d.code(
        "客户端                          服务端\n"
        "  │  POST /api/v1/auth/login      │\n"
        "  │  {username, password}         │\n"
        "  │──────────────────────────────►│\n"
        "  │                               │ ① AuthenticationManager.authenticate\n"
        "  │                               │    → UserDetailsServiceImpl 查库\n"
        "  │                               │    → PasswordEncoder 比对密码\n"
        "  │                               │ ② 生成 JWT（uid/role/nickname/exp）\n"
        "  │◄──────────────────────────────│\n"
        "  │  {token, userId, role, ...}   │\n"
        "  │                               │\n"
        "  │  GET /api/v1/attendance/recent│\n"
        "  │  Authorization: Bearer xxx    │\n"
        "  │──────────────────────────────►│\n"
        "  │                               │ ③ JwtAuthenticationFilter 解析令牌\n"
        "  │                               │ ④ 构造 LoginUser 写入 SecurityContext\n"
        "  │                               │ ⑤ @PreAuthorize / SecurityUtils 读取\n"
        "  │◄──────────────────────────────│",
    )

    d.h3("3.2.2 令牌里放什么")
    d.table(
        ["Claim", "内容", "用途"],
        [
            ["sub", "username", "标识身份，日志排查用"],
            ["uid", "用户主键", "业务层直接取当前用户 ID"],
            ["role", "admin / manager / member", "构造 GrantedAuthority，支持 hasRole 表达式"],
            ["nickname", "昵称", "顶栏展示，避免每次都查库"],
            ["iss", "teampulse", "签发者校验，防止其他系统签发的令牌混用"],
            ["iat / exp", "签发/过期时间", "默认 12 小时"],
        ],
        widths=[2.6, 6.0, 7.8],
    )
    d.p(
        "**为什么把 role 放进令牌而不是每次查库**：无状态的核心价值就是「不查库」。"
        "代价是角色变更后旧令牌仍然按旧角色生效，直到过期（最长 12 小时）。"
        "如果业务要求立即生效，就需要引入令牌黑名单或把 TTL 缩短到分钟级 + refresh token——"
        "**这是一个明确的权衡，说出来比假装没有更好**。"
    )

    d.h3("3.2.3 两个实现细节")
    d.p("**① 密钥长度在启动时就校验**。HS256 要求密钥 ≥ 256 位，短密钥会让签名可被暴力破解：")
    d.code(
        "@PostConstruct\n"
        "void init() {\n"
        "    byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);\n"
        "    if (keyBytes.length < 32) {\n"
        "        throw new IllegalStateException(\"teampulse.jwt.secret 长度不足 32 字节...\");\n"
        "    }\n"
        "    this.secretKey = Keys.hmacShaKeyFor(keyBytes);\n"
        "}",
        caption="宁可启动失败，也不要带着弱密钥上线",
    )
    d.p("**② 解析失败不抛异常，返回 null**。过滤器里保持匿名，让授权规则去拒绝并触发统一的 401 JSON 响应，"
        "这样前端拦截器只需要处理一种错误格式。")

    # ---------------- 3.3 ----------------
    d.h2("3.3 Spring Security 过滤器链配置")
    d.p("这是整个安全体系里最容易配错的地方，我把它拆成四个决策点：")
    d.table(
        ["配置项", "取值", "原因"],
        [
            ["csrf", "disable", "纯 Token 认证、不用 Cookie 会话，CSRF 攻击面不存在；开着反而要求前端额外带 token"],
            ["sessionManagement", "STATELESS", "不创建 HttpSession，天然支持多实例水平扩容"],
            ["cors", "从配置读取允许来源，生产同源则留空", "开发期前端 5173 直连后端需要跨域；生产由 Nginx 同源反代，不需要"],
            ["authorizeHttpRequests", "白名单 + 其余 authenticated", "/api/v1/auth/**、/iot/**、/actuator/health、swagger 放行"],
        ],
        widths=[3.4, 6.2, 6.8],
    )
    d.code(
        "http\n"
        "  .csrf(AbstractHttpConfigurer::disable)\n"
        "  .cors(cors -> cors.configurationSource(corsConfigurationSource()))\n"
        "  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n"
        "  .authorizeHttpRequests(auth -> auth\n"
        "      .requestMatchers(HttpMethod.OPTIONS, \"/**\").permitAll()\n"
        "      .requestMatchers(\"/api/v1/auth/**\").permitAll()\n"
        "      .requestMatchers(\"/iot/**\").permitAll()      // 设备走 HMAC 签名，不走 JWT\n"
        "      .requestMatchers(\"/actuator/health\", \"/actuator/info\").permitAll()\n"
        "      .requestMatchers(\"/v3/api-docs/**\", \"/swagger-ui/**\").permitAll()\n"
        "      .anyRequest().authenticated())\n"
        "  .exceptionHandling(ex -> ex\n"
        "      .authenticationEntryPoint(...)   // 401 也返回统一 JSON\n"
        "      .accessDeniedHandler(...))       // 403 同款\n"
        "  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);",
        caption="backend/.../config/SecurityConfig.java",
    )
    d.callout(
        "为什么 /iot/** 可以放行",
        "「放行」指的是不需要用户 JWT，而不是不校验身份。设备请求必须在业务层通过 HMAC 验签 + 时钟漂移 + nonce 去重三道校验，"
        "实际安全强度不低于 JWT。这一点如果不主动解释，面试官会认为你留下了未授权接口。",
    )

    # ---------------- 3.4 ----------------
    d.h2("3.4 密码安全：BCrypt 与平滑迁移")
    d.h3("3.4.1 为什么不能用明文，也不能用 MD5")
    d.table(
        ["方案", "问题"],
        [
            ["明文", "一次拖库即全部泄露；且员工常在多个系统用同一密码，危害外溢"],
            ["MD5 / SHA1", "无盐、计算极快，GPU 每秒可算数十亿次，彩虹表可直接反查"],
            ["SHA256 + 固定盐", "仍有彩虹表风险；固定盐意味着相同密码散列相同"],
            ["BCrypt（本项目）", "自带随机盐、可调计算强度（cost），故意设计得慢，暴力破解成本极高"],
        ],
        widths=[3.8, 12.6],
    )

    d.h3("3.4.2 真正的难点：存量明文密码怎么办")
    d.p(
        "直接切 BCrypt 会让所有老账号登录失败。我采用的是 **「读时兼容、写时升级」**："
        "用户完全无感知，下次登录时密码自动变成 BCrypt 密文。"
    )
    d.code(
        "// MigratingPasswordEncoder\n"
        "public boolean matches(CharSequence raw, String encoded) {\n"
        "    if (isBcrypt(encoded)) return delegate.matches(raw, encoded);  // 新数据走 BCrypt\n"
        "    return raw.toString().equals(encoded);                          // 老数据按明文比对\n"
        "}\n"
        "\n"
        "@Override\n"
        "public boolean upgradeEncoding(String encoded) {\n"
        "    return !isBcrypt(encoded);   // 告诉框架「这条密文需要升级」\n"
        "}",
    )
    d.p("升级动作由 Spring Security 自己完成，不需要我写「登录成功后手动 update」：")
    d.code(
        "DaoAuthenticationProvider provider = new DaoAuthenticationProvider();\n"
        "provider.setUserDetailsService(userDetailsService);\n"
        "provider.setPasswordEncoder(passwordEncoder);\n"
        "// 关键：登录成功后，框架会回调 UserDetailsPasswordService#updatePassword\n"
        "provider.setUserDetailsPasswordService(userDetailsService);",
        caption="UserDetailsServiceImpl 同时实现了 UserDetailsService 和 UserDetailsPasswordService",
    )
    d.callout(
        "面试价值",
        "这一段是很好的加分点：说明你不仅会用 BCrypt，还知道 DaoAuthenticationProvider 的密码升级扩展点，"
        "并且理解「不能要求用户重置密码」这个产品约束。可以主动讲「我是在重构存量系统时遇到这个问题的」。",
    )

    # ---------------- 3.5 ----------------
    d.h2("3.5 授权模型：三层权限")
    d.p("这个项目没有做复杂的 RBAC 表（原项目的 permission / permission_action 建了表却从没接入鉴权），"
        "而是用「系统角色 + 团队角色 + 数据归属」三层组合，覆盖了全部业务场景。")
    d.table(
        ["层", "取值", "判定方式", "举例"],
        [
            ["系统角色", "admin / manager / member", "@PreAuthorize(\"hasRole('ADMIN')\")", "用户管理、设备管理、考勤规则"],
            ["团队角色", "leader / member", "查询 team_member 表判定", "改团队信息、增删成员"],
            ["数据归属", "创建者本人", "compare operatorId", "改/删自己的日程、日志、评论"],
        ],
        widths=[2.4, 4.6, 5.4, 6.0],
    )
    d.p("业务代码里取当前用户只有两个入口，杜绝「从请求参数读 userId」这种伪鉴权：")
    d.code(
        "SecurityUtils.currentUserId();          // 取不到直接 401\n"
        "SecurityUtils.requireSelfOrAdmin(id);  // 不是本人也不是 admin → 403",
    )

    # ---------------- 3.6 ----------------
    d.h2("3.6 其他常见攻击面与应对")
    d.table(
        ["攻击", "本项目如何防护", "在哪看"],
        [
            ["SQL 注入", "全部使用 MyBatis-Plus 参数化（#{}），无字符串拼接；"
                        "LIMIT 只接受校验过的整数", "所有 Mapper"],
            ["XSS", "Vue 默认对插值转义；不使用 v-html 渲染用户输入", "前端 views"],
            ["CSRF", "无 Cookie 会话 + Security 关闭 CSRF", "SecurityConfig"],
            ["越权访问", "接口级 @PreAuthorize + 服务级 requireSelfOrAdmin", "UserController / ScheduleService"],
            ["密码爆破", "登录失败不区分「用户不存在/密码错」；可扩展失败次数限流（Redis）", "AuthService"],
            ["设备伪造", "HMAC-SHA256 + nonce + 时钟漂移", "第 2 章"],
            ["敏感内容", "评论入库前做敏感词过滤，原文单独留存便于审计", "SensitiveWordService"],
            ["重放攻击", "nonce 去重（Redis SETNX）+ 时间窗", "NonceDedupService"],
            ["容器逃逸", "Dockerfile 以非 root 用户运行", "backend/Dockerfile"],
            ["信息泄露", "生产关闭 Swagger；/actuator 仅内网可访问；secret 只下发一次", "application-prod.yml / nginx.conf"],
        ],
        widths=[2.4, 9.4, 4.6],
    )

    # ---------------- 3.7 ----------------
    d.h2("3.7 安全自查清单（部署前逐项确认）")
    d.bullets(
        [
            "JWT_SECRET 是 32 字节以上随机值，且**没有**提交到 Git",
            "初始管理员密码已修改，或 TPP_BOOTSTRAP_ENABLED 已关闭",
            "MySQL 端口只绑定 127.0.0.1，公网无法直连",
            "MQTT 已开启密码认证（allow_anonymous false），默认账号已改",
            "设备密钥没有出现在任何前端响应里（列表接口已置空）",
            "生产 SPRING_PROFILES_ACTIVE=prod，Swagger 关闭",
            "Nginx 已加 X-Content-Type-Options / X-Frame-Options 等响应头",
            "数据库每日备份可用，且至少演练过一次恢复",
            "SSH 密钥登录、禁用 root 密码登录，云安全组只放行 80/443/1883/22",
        ]
    )
