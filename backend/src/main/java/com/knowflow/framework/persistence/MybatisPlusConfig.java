package com.knowflow.framework.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页拦截器。
 *
 * <p>分页方言不再写死，而是根据本次查询连接的元数据选择， 使切库后 LIMIT 语法由 MyBatis-Plus 按对应方言生成。
 */
@Configuration
public class MybatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
