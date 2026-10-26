package com.teampulse.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <ul>
 *   <li>{@link PaginationInnerInterceptor}：物理分页，必须显式声明数据库类型，
 *       否则 count 语句会与 limit 语句拼接错误</li>
 *   <li>{@link BlockAttackInnerInterceptor}：拦截「无 where 条件的 update/delete」，
 *       防止一次误调用清空整张表</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大 500 条，防止前端传 size=999999 打爆内存
        pagination.setMaxLimit(500L);
        // 页码超出总页数时回到首页，避免返回空表让用户以为没数据
        pagination.setOverflow(true);
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
