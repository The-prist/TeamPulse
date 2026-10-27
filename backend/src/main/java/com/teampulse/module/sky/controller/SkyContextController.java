package com.teampulse.module.sky.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teampulse.common.api.ApiResponse;
import com.teampulse.module.sky.dto.SkyDtos;
import com.teampulse.module.sky.service.SkyContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 登录页天空相关公开接口（无需登录）。
 *
 * <p>只提供「氛围渲染」所需的位置与天气信息，不包含任何业务数据。
 */
@Slf4j
@Tag(name = "登录页天空", description = "访客位置、当前天气、行政区划树")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class SkyContextController {

    private final SkyContextService skyContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 行政区划树：classpath:data/pca-code.json，首次读取后常驻内存 */
    private volatile JsonNode regionTree;

    @Operation(summary = "访客位置与当前天气",
            description = "不传参数时按访客真实 IP 定位；传 province/city/district 时按手动选择的地点取天气")
    @GetMapping("/sky-context")
    public ApiResponse<SkyDtos.SkyContext> skyContext(
            HttpServletRequest request,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district) {
        return ApiResponse.ok(skyContextService.resolve(request, province, city, district));
    }

    @Operation(summary = "行政区划树（省/市/区三级）", description = "用于登录页的城市选择表单")
    @GetMapping("/regions")
    public ApiResponse<JsonNode> regions() {
        JsonNode tree = regionTree;
        if (tree == null) {
            synchronized (this) {
                if (regionTree == null) {
                    regionTree = loadRegions();
                }
                tree = regionTree;
            }
        }
        return ApiResponse.ok(tree);
    }

    private JsonNode loadRegions() {
        try (InputStream in = new ClassPathResource("data/pca-code.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("行政区划数据读取失败: {}", e.toString());
            return objectMapper.createArrayNode();
        }
    }
}
