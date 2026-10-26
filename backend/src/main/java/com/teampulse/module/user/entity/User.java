package com.teampulse.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.teampulse.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 *
 * <p>{@code password} 标记为 WRITE_ONLY：可以被请求体反序列化写入，
 * 但任何序列化输出（列表、详情、登录响应）都不会带出密码散列。
 * {@code employeeNo} 是物联网打卡机的匹配键，与 ESP32 上报的员工号一一对应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;

    private String email;

    private String phone;

    /** 员工工号，打卡机上报此值；允许为空（非考勤员工） */
    private String employeeNo;

    /** 系统角色标识：sysadmin / hr / manager / leader / employee / auditor */
    private String role;

    /** 所属部门，参与数据范围（DEPT）判定 */
    private Long departmentId;

    /** 账号状态：active / inactive */
    private String status;

    private String avatar;
}
