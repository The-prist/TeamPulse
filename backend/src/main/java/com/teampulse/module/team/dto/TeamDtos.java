package com.teampulse.module.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队模块的请求体与视图对象。
 *
 * <p>集中放在一个容器类里，避免为 6 个只有几行的类各开一个文件；
 * 校验注解全部带中文提示，错误信息可以直接抛给最终用户看。
 */
public final class TeamDtos {

    private TeamDtos() {
    }

    /** 创建团队：只有名称必填，描述可选 */
    @Data
    public static class CreateRequest {

        @NotBlank(message = "团队名称不能为空")
        @Size(max = 100, message = "团队名称不能超过 100 字")
        private String teamName;

        @Size(max = 2000, message = "团队描述不能超过 2000 字")
        private String description;
    }

    /** 更新团队：null 表示不修改，空串表示清空（描述允许被清空） */
    @Data
    public static class UpdateRequest {

        @Size(max = 100, message = "团队名称不能超过 100 字")
        private String teamName;

        @Size(max = 2000, message = "团队描述不能超过 2000 字")
        private String description;
    }

    /** 添加成员：teamId 与路径重复，是为了让前端在请求体里也能自描述 */
    @Data
    public static class MemberAddRequest {

        @NotNull(message = "团队ID不能为空")
        private Long teamId;

        @NotNull(message = "用户ID不能为空")
        private Long userId;

        /** 默认为普通成员：负责人是要显式指定的，否则人人都能拿管理权限 */
        @Pattern(regexp = "^(leader|member)$", message = "团队角色只能是 leader/member")
        private String teamRole = "member";
    }

    /** 调整成员在团队内的角色 */
    @Data
    public static class MemberRoleUpdateRequest {

        @NotBlank(message = "团队角色不能为空")
        @Pattern(regexp = "^(leader|member)$", message = "团队角色只能是 leader/member")
        private String teamRole;
    }

    /**
     * 团队视图对象。
     *
     * <p>{@code memberCount} / {@code leaderName} 是跨表派生字段，前端列表页要直接展示，
     * 因此由服务端一次查询补齐，而不是让页面按团队再发 N 个请求。
     * {@code myRole} 只在「我参与的团队」等需要区分视角的接口里填充，管理端列表为 null。
     */
    @Data
    public static class TeamVO {

        private Long teamId;
        private String teamName;
        private String description;
        private Long creatorId;
        private LocalDateTime createdAt;
        /** 有效成员数（status = active） */
        private Long memberCount;
        /** 负责人昵称，团队没有负责人时为 null */
        private String leaderName;
        /** 当前登录人在该团队的角色：leader / member / null */
        private String myRole;
    }

    /**
     * 成员视图对象。
     *
     * <p>只暴露展示所需字段，不带密码、邮箱、角色等敏感信息——
     * 团队页是普通成员也能看的页面，不该借它泄露通讯录。
     */
    @Data
    public static class MemberVO {

        private Long userId;
        private String username;
        private String nickname;
        private String employeeNo;
        /** 团队内角色：leader / member */
        private String teamRole;
        /** 成员状态：active / inactive */
        private String status;
        private LocalDateTime joinTime;
    }
}
