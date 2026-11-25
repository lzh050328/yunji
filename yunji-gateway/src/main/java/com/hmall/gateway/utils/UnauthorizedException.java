package com.hmall.gateway.utils;

/**
 * 未登录 / token 无效异常
 * 网关不依赖 hm-common（Servlet 技术栈与 WebFlux 冲突），这里提供与单体等价的异常类型，
 * 保证 JwtTool 的校验分支行为保持一致。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
