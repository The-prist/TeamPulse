package com.teampulse.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户管理相关请求体。全部走 Bean Validation，Controller 不再手写 if 判空。
 */
public final class UserDtos {

    private UserDtos() {
    }

    /** 管理员创建用户 */
    @Data
    public static class CreateRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
        private String password;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        private String nickname;
        private String phone;

        /** 员工工号，可为空；为空则该用户不参与考勤 */
        @Pattern(regexp = "^[A-Za-z0-9_-]{0,32}$", message = "工号只能包含字母、数字、下划线与短横线")
        private String employeeNo;

        @Pattern(regexp = "^(sysadmin|hr|manager|leader|employee|auditor)$", message = "角色取值非法")
        private String role = "member";

        @Pattern(regexp = "^(active|inactive)$", message = "状态只能是 active/inactive")
        private String status = "active";
    }

    /** 管理员更新用户（null 字段表示不修改） */
    @Data
    public static class UpdateRequest {
        private String nickname;
        @Email(message = "邮箱格式不正确")
        private String email;
        private String phone;
        @Pattern(regexp = "^[A-Za-z0-9_-]{0,32}$", message = "工号只能包含字母、数字、下划线与短横线")
        private String employeeNo;
        @Pattern(regexp = "^(sysadmin|hr|manager|leader|employee|auditor)$", message = "角色取值非法")
        private String role;

    /** 所属部门，参与数据范围（DEPT）判定 */
    private Long departmentId;
        @Pattern(regexp = "^(active|inactive)$", message = "状态只能是 active/inactive")
        private String status;
    }

    /** 自助更新个人资料（不允许改角色/状态） */
    @Data
    public static class SelfUpdateRequest {
        @Size(max = 50, message = "昵称过长")
        private String nickname;
        @Email(message = "邮箱格式不正确")
        private String email;
        private String phone;
        private String avatar;
    }

    /** 修改密码 */
    @Data
    public static class PasswordChangeRequest {
        @NotBlank(message = "旧密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需在 6~64 之间")
        private String newPassword;
    }

    /** 管理员直接重置密码（无需旧密码） */
    @Data
    public static class PasswordResetRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需在 6~64 之间")
        private String newPassword;
    }
}
