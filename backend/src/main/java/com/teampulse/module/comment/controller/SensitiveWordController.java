package com.teampulse.module.comment.controller;

import com.teampulse.common.api.ApiResponse;
import com.teampulse.common.api.PageResult;
import com.teampulse.module.comment.entity.SensitiveWord;
import com.teampulse.module.comment.service.SensitiveWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.teampulse.module.audit.annotation.Audited;

/**
 * 敏感词库管理接口。
 *
 * <p>整个类加 {@code @PreAuthorize("hasAuthority('sensitive:manage')")}：词库决定全站评论展示什么，
 * 属于管理端能力，普通成员连读取词库都不应该（否则等于把一份「可绕过清单」发给所有人）。
 * 唯一的例外是 {@code /check}，但它也只在这个类里，供管理端编辑评论时预检。
 *
 * <p>两个请求体以静态内部类就地定义：它们只服务于本控制器，
 * 放进独立 dto 文件反而会为了 2 个类多一个文件，阅读时需要来回跳转。
 */
@Tag(name = "敏感词管理", description = "词库 CRUD 与文本命中检查（仅管理员）")
@RestController
@RequestMapping("/api/v1/sensitive-word")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('sensitive:manage')")
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "敏感词分页查询（管理员）", description = "keyword 同时匹配词与替换文本")
    @GetMapping
    public ApiResponse<PageResult<SensitiveWord>> page(@RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(sensitiveWordService.page(keyword, page, size));
    }

    @Operation(summary = "新增敏感词（管理员）", description = "重复录入返回 409；新增后缓存立即失效")
    @PostMapping
    @Audited(module = "sensitive", action = "sensitive.create", detail = "新增敏感词")
    public ApiResponse<Map<String, Object>> add(@Valid @RequestBody WordCreateRequest request) {
        SensitiveWord word = sensitiveWordService.add(request.getWord(), request.getReplacement());
        return ApiResponse.ok(Map.of("wordId", word.getWordId()));
    }

    @Operation(summary = "更新敏感词（管理员）", description = "word/replacement/status 传 null 表示不修改")
    @PutMapping("/{wordId}")
    @Audited(module = "sensitive", action = "sensitive.update", detail = "修改敏感词")
    public ApiResponse<Void> update(@PathVariable Long wordId,
                                    @Valid @RequestBody WordUpdateRequest request) {
        sensitiveWordService.update(wordId, request.getWord(), request.getReplacement(), request.getStatus());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除敏感词（管理员）", description = "物理删除词条，历史评论的替换结果不回溯")
    @DeleteMapping("/{wordId}")
    @Audited(module = "sensitive", action = "sensitive.delete", detail = "删除敏感词")
    public ApiResponse<Void> delete(@PathVariable Long wordId) {
        sensitiveWordService.delete(wordId);
        return ApiResponse.ok();
    }

    @Operation(summary = "文本命中检查（管理员）",
            description = "供前端在编辑框实时提示：返回 {hit: 是否命中, words: 命中词列表}，不落库")
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> check(@RequestParam String content) {
        List<String> words = sensitiveWordService.findWords(content);
        return ApiResponse.ok(Map.<String, Object>of("hit", !words.isEmpty(), "words", words));
    }

    /** 新增敏感词请求体 */
    @Data
    public static class WordCreateRequest {

        @NotBlank(message = "敏感词不能为空")
        @Size(max = 100, message = "敏感词不能超过 100 字")
        private String word;

        /** 留空时服务层回落为 *** */
        @Size(max = 100, message = "替换文本不能超过 100 字")
        private String replacement;
    }

    /** 更新敏感词请求体：全字段可选，null 表示不修改 */
    @Data
    public static class WordUpdateRequest {

        @Size(max = 100, message = "敏感词不能超过 100 字")
        private String word;

        @Size(max = 100, message = "替换文本不能超过 100 字")
        private String replacement;

        @Pattern(regexp = "^(active|inactive)$", message = "状态只能是 active 或 inactive")
        private String status;
    }
}
