package com.teampulse.module.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「需要留痕」的写操作。
 *
 * <p>用法：加在 Controller 方法上即可，切面会自动记录操作人、traceId、IP、
 * 耗时、成功/失败，不需要在业务代码里写任何日志语句。
 *
 * <pre>{@code
 * @Audited(module = "user", action = "user.create", detail = "新增用户")
 * @PostMapping
 * public ApiResponse<...> create(...) { ... }
 * }</pre>
 *
 * <p>约定：{@code action} 用 {@code module.动词} 形式，前端按它做筛选与展示。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 模块：user / team / schedule / attendance / device / comment / sensitive ... */
    String module();

    /** 动作标识，建议 module.动词，如 user.delete、attendance.rule.update */
    String action();

    /** 变更要点（静态描述），写入 detail 字段 */
    String detail() default "";
}
