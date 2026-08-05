package com.knowflow.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.knowflow.config.datasource.DynamicRoutingDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页拦截器。
 * <p>
 * 分页方言不再写死，而是跟随当前生效的数据源类型（H2 / MySQL）自动选择，
 * 使切库后 LIMIT 语法由 MyBatis-Plus 按对应方言生成。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DynamicRoutingDataSource dynamicRoutingDataSource) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(dynamicRoutingDataSource.getCurrentType().getDbType());
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
