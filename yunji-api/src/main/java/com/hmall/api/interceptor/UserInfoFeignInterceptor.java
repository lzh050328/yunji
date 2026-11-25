package com.hmall.api.interceptor;

import com.hmall.common.utils.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeign 用户信息拦截器
 * 服务间调用（如 pay-service 调 user-service 扣款）不经过网关，拿不到网关注入的 user-info 头，
 * 这里从当前线程的 UserContext 取出用户 id，补充到 Feign 请求头上，保证整条链路用户身份一致
 *
 * 由 FeignUserInfoConfig 注册到各个 Feign 客户端，见 @FeignClient(configuration = ...)
 */
@Slf4j
public class UserInfoFeignInterceptor implements RequestInterceptor {

    /**
     * 用户信息请求头，与网关 AuthGlobalFilter、下游 UserInfoInterceptor 约定一致
     */
    public static final String USER_INFO_HEADER = "user-info";

    @Override
    public void apply(RequestTemplate template) {
        // 1.从当前线程上下文获取用户 id
        Long userId = UserContext.getUser();
        // 2.能获取到才传递，避免把 null 写到下游
        if (userId != null) {
            template.header(USER_INFO_HEADER, userId.toString());
            log.debug("Feign 透传用户信息：{}={} -> {}", USER_INFO_HEADER, userId, template.url());
        } else {
            log.debug("Feign 透传用户信息：当前线程无用户上下文，不传递 {}", template.url());
        }
    }
}
