package com.hmall.common.config;

import com.hmall.common.interceptor.UserInfoInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户信息拦截器自动装配
 * 注册进 spring.factories，依赖 hm-common 的 Servlet 微服务会自动生效；
 * 网关不依赖 hm-common，不受影响
 */
@Configuration
@ConditionalOnClass(HandlerInterceptor.class)
public class UserInfoInterceptorAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceptor())
                .addPathPatterns("/**");
    }
}
