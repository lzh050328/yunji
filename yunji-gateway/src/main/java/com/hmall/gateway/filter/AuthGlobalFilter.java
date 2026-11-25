package com.hmall.gateway.filter;

import cn.hutool.core.collection.CollUtil;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 登录校验全局过滤器
 * 逻辑对齐单体 LoginInterceptor：取 authorization 头 → 校验 token → 放行
 * 单体用 UserContext 存用户（同进程 ThreadLocal），网关是独立进程，改为写 user-info 请求头传给下游
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 与单体 MvcConfig 中固定放行的框架路径保持一致
     */
    private static final List<String> STATIC_PATHS = List.of(
            "/error",
            "/favicon.ico",
            "/v2/**",
            "/v3/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/doc.html"
    );

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1.白名单直接放行
        if (isExcludePath(path)) {
            return chain.filter(exchange);
        }

        // 2.获取请求头中的 token
        String token = request.getHeaders().getFirst("authorization");

        // 3.校验 token
        Long userId;
        try {
            userId = jwtTool.parseToken(token);
        } catch (Exception e) {
            log.warn("token 校验失败，path：{}，原因：{}", path, e.getMessage());
            return unauthorized(exchange, e.getMessage());
        }

        // 4.把用户信息放入请求头，传递给下游服务
        ServerHttpRequest newRequest = request.mutate()
                .header("user-info", userId.toString())
                .build();

        // 5.放行
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    /**
     * 判断是否为放行路径
     * 采用 AntPathMatcher 匹配，与单体的路径匹配规则一致
     */
    private boolean isExcludePath(String path) {
        List<String> excludePaths = authProperties.getExcludePaths();
        if (CollUtil.isNotEmpty(excludePaths)
                && excludePaths.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path))) {
            return true;
        }
        return STATIC_PATHS.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 返回 401，响应体格式与 hm-common 的全局异常处理保持一致
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":401,\"msg\":\"%s\",\"data\":null}", msg);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
