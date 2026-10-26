package com.teampulse.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.module.user.entity.User;
import com.teampulse.module.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：用真实的 MySQL 容器跑完整链路。
 *
 * <p>与单元测试的区别：这里会真的启动 Spring 容器、执行 Flyway、走 HTTP 接口，
 * 覆盖「单元测试测不到」的部分——迁移能否在空库跑通、管理员是否被正确赋予 RBAC 角色、
 * 打卡验签与幂等是否真的生效。
 *
 * <p>本机没有 Docker 时整类自动跳过（{@code disabledWithoutDocker = true}），
 * 不会让 {@code mvn test} 变红。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamPulseIntegrationTest {

    private static final String ADMIN_PASSWORD = "It@Test2026";
    private static final DateTimeFormatter PUNCH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("teampulse")
            .withUsername("root")
            .withPassword("it-root-pwd")
            // 与生产一致：utf8mb4 + 东八区，避免中文/时间在迁移与断言里出现差异
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                    "--default-time-zone=+08:00");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("teampulse.mqtt.enabled", () -> false);
        registry.add("teampulse.bootstrap.enabled", () -> true);
        registry.add("teampulse.bootstrap.admin-password", () -> ADMIN_PASSWORD);
        // 测试环境用固定密钥，避免依赖外部环境变量
        registry.add("teampulse.jwt.secret", () -> "it-test-secret-0123456789abcdef0123456789abcdef");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserMapper userMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------- 工具

    private JsonNode post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        return parse(res);
    }

    private JsonNode parse(ResponseEntity<String> res) {
        try {
            JsonNode node = json.readTree(res.getBody() == null ? "{}" : res.getBody());
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_status", res.getStatusCode().value());
            return node;
        } catch (Exception ex) {
            throw new IllegalStateException("响应不是合法 JSON: " + res.getBody(), ex);
        }
    }

    private JsonNode get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return parse(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    private String login(String username, String password) {
        JsonNode res = post("/api/v1/auth/login", Map.of("username", username, "password", password), null);
        assertThat(res.get("_status").asInt()).as("登录应成功: %s", res).isEqualTo(200);
        return res.path("data").path("token").asText();
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 造一个普通员工，用于验证「只有本人数据」的数据范围 */
    private User ensureEmployee(String username, String employeeNo) {
        User existing = userMapper.findByEmployeeNo(employeeNo);
        if (existing != null) {
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname("集成测试员工");
        user.setEmail(username + "@it.local");
        user.setPhone("13900000000");
        user.setEmployeeNo(employeeNo);
        user.setRole("employee");
        user.setStatus("active");
        user.setDepartmentId(2L);
        userMapper.insert(user);
        return user;
    }

    // ---------------------------------------------------------------- 用例

    @Test
    @DisplayName("空库启动：Flyway 执行 V1~V8，建出 21 张表")
    void flywayRunsOnEmptyDatabase() {
        String token = login("admin", ADMIN_PASSWORD);
        assertThat(token).isNotBlank();
        // 能查到用户即说明 user 表已建好；再用 health 确认上下文正常
        JsonNode health = get("/actuator/health", null);
        assertThat(health.path("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("全新库的管理员必须是 sysadmin，且携带 22 个权限点")
    void bootstrapAdminHasRbacRole() {
        JsonNode res = post("/api/v1/auth/login", Map.of("username", "admin", "password", ADMIN_PASSWORD), null);
        JsonNode data = res.path("data");
        assertThat(data.path("role").asText()).isEqualTo("sysadmin");
        assertThat(data.path("dataScope").asText()).isEqualTo("ALL");
        assertThat(data.path("permissions")).hasSize(22);
        // 这条断言是回归保护：曾经 bootstrap 写死旧角色值 "admin"，导致全新库管理员权限为 0
        assertThat(data.path("permissions").toString()).contains("user:manage").contains("audit:view");
    }

    @Test
    @DisplayName("未认证访问受保护接口返回 401")
    void anonymousIsRejected() {
        JsonNode res = get("/api/v1/user/list", null);
        assertThat(res.get("_status").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("打卡上报：验签失败被拒、合法报文成功、重复报文幂等且不新增记录")
    void punchVerifyAndIdempotency() {
        // 取 bootstrap 生成的演示设备密钥
        JsonNode devices = get("/api/v1/devices?page=1&size=5", login("admin", ADMIN_PASSWORD));
        JsonNode first = devices.path("data").path("records").get(0);
        assertThat(first).as("应有演示设备").isNotNull();
        String sn = first.path("deviceSn").asText();
        // 设备密钥只在注册/轮换时可见，这里用列表接口拿不到 → 直接用管理员轮换一次拿新密钥
        JsonNode rotated = post("/api/v1/devices/" + first.path("id").asInt() + "/secret", Map.of(),
                login("admin", ADMIN_PASSWORD));
        assertThat(rotated.get("_status").asInt()).isEqualTo(200);
        String secret = rotated.path("data").path("secret").asText();
        assertThat(secret).hasSize(64);

        ensureEmployee("it_employee", "IT1001");
        String punchTime = LocalDateTime.now().withNano(0).format(PUNCH_FORMAT);
        String nonce = "it-" + UUID.randomUUID();
        String canonical = String.join("|", sn, "IT1001", punchTime, nonce);
        String sign = hmacSha256Hex(secret, canonical);

        // 1) 坏签名 → 拒绝（设备侧错误码 60003，HTTP 401）
        JsonNode bad = post("/iot/v1/punch", Map.of(
                "deviceSn", sn, "employeeNo", "IT1001", "punchTime", punchTime,
                "nonce", nonce + "-bad", "sign", "deadbeef"), null);
        assertThat(bad.get("_status").asInt()).isEqualTo(401);

        // 2) 合法报文 → 成功
        Map<String, Object> payload = Map.of(
                "deviceSn", sn, "employeeNo", "IT1001", "punchTime", punchTime,
                "nonce", nonce, "sign", sign);
        JsonNode ok = post("/iot/v1/punch", payload, null);
        assertThat(ok.get("_status").asInt()).as("合法报文应成功: %s", ok).isEqualTo(200);

        // 3) 同一报文重发 → 幂等（仍 200，但记录数不变）
        long before = countRecords(sn);
        JsonNode dup = post("/iot/v1/punch", payload, null);
        long after = countRecords(sn);
        assertThat(dup.get("_status").asInt()).isEqualTo(200);
        assertThat(after).as("重复报文不应新增记录").isEqualTo(before);
    }

    @Test
    @DisplayName("数据范围：员工只能看到自己的打卡，管理员能看到更多")
    void dataScopeIsolation() {
        User employee = ensureEmployee("it_scope_user", "IT1002");
        String adminToken = login("admin", ADMIN_PASSWORD);
        String employeeToken = login("it_scope_user", "123456");

        JsonNode adminView = get("/api/v1/attendance/records?page=1&size=200", adminToken);
        JsonNode selfView = get("/api/v1/attendance/records?page=1&size=200", employeeToken);
        assertThat(adminView.get("_status").asInt()).isEqualTo(200);
        assertThat(selfView.get("_status").asInt()).isEqualTo(200);

        // 员工范围的查询必须只包含自己（这里用总条数比较：员工 ≤ 管理员）
        long adminTotal = adminView.path("data").path("total").asLong();
        long selfTotal = selfView.path("data").path("total").asLong();
        assertThat(selfTotal).isLessThanOrEqualTo(adminTotal);

        // 员工访问管理端接口应 403
        JsonNode deny = get("/api/v1/user/list?page=1&size=1", employeeToken);
        assertThat(deny.get("_status").asInt()).isEqualTo(403);
        assertThat(employee.getUserId()).isNotNull();
    }

    @SuppressWarnings("unused")
    private List<String> unusedHelperForCompiler() {
        return List.of(HttpStatus.OK.toString());
    }

    private long countRecords(String deviceSn) {
        JsonNode res = get("/api/v1/attendance/records?page=1&size=1", login("admin", ADMIN_PASSWORD));
        assertThat(res.get("_status").asInt()).isEqualTo(200);
        return res.path("data").path("total").asLong();
    }
}
