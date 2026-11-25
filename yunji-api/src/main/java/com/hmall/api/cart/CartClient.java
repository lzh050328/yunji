package com.hmall.api.cart;

import com.hmall.api.config.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;

/**
 * 购物车服务远程调用客户端
 * 对应 cart-service 的 DELETE /carts?ids=xxx 批量删除接口
 */
@FeignClient(name = "cart-service", path = "/carts", configuration = FeignCommonConfig.class)
public interface CartClient {

    @DeleteMapping
    void removeByItemIds(@RequestParam("ids") Collection<Long> ids);
}
