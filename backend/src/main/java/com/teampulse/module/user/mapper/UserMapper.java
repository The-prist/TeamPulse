package com.teampulse.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teampulse.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。
 *
 * <p>绝大多数查询用 MyBatis-Plus 的 LambdaQueryWrapper 完成，
 * 这里只保留两处非要手写 SQL 的场景：工号反查（打卡链路高频调用）与下拉选项瘦字段查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按工号查用户（打卡报文解析用），走 uk_user_employee_no 唯一索引 */
    @Select("SELECT user_id, username, nickname, employee_no, role, status, deleted "
            + "FROM `user` WHERE employee_no = #{employeeNo} AND deleted = 0 LIMIT 1")
    User findByEmployeeNo(@Param("employeeNo") String employeeNo);

    /** 下拉选项：只查需要的列，避免把整表密码散列读进内存 */
    @Select("SELECT user_id, username, nickname, employee_no FROM `user` "
            + "WHERE deleted = 0 AND status = 'active' ORDER BY user_id")
    List<User> listOptions();
}
