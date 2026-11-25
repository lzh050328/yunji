package com.hmall.api.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.api.interceptor.UserInfoFeignInterceptor;
import com.hmall.common.exception.BizIllegalException;
import feign.RequestInterceptor;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

/**
 * Feign 公共配置：
 * 1. 用户信息透传拦截器
 * 2. 错误解码器——把下游服务返回的 {code,msg} 解析成本地业务异常，
 *    避免跨服务调用后错误信息被统一吞成「服务器内部异常」
 *
 * 通过 @FeignClient(configuration = FeignCommonConfig.class) 挂到每个客户端
 * 注意：此类不加 @Configuration，避免被主容器的组件扫描加载成全局配置
 */
public class FeignCommonConfig {

    @Bean
    public UserInfoFeignInterceptor userInfoFeignInterceptor() {
        return new UserInfoFeignInterceptor();
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            String body = "";
            try {
                if (response.body() != null) {
                    body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
                // 读取失败则退化为通用提示
            }
            if (StrUtil.isNotBlank(body)) {
                try {
                    var msg = JSONUtil.parseObj(body).getStr("msg");
                    if (StrUtil.isNotBlank(msg)) {
                        return new BizIllegalException(msg);
                    }
                } catch (Exception ignored) {
                    // 非标准响应体，退化为通用提示
                }
            }
            return new BizIllegalException("远程服务调用失败，状态码：" + response.status());
        };
    }
}
