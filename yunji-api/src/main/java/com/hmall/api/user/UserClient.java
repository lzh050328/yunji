package com.hmall.api.user;

import com.hmall.api.config.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务远程调用客户端
 * 对应 user-service 的扣减余额接口
 */
@FeignClient(name = "user-service", path = "/users", configuration = FeignCommonConfig.class)
public interface UserClient {

    @PutMapping("/money/deduct")
    void deductMoney(@RequestParam("pw") String pw, @RequestParam("amount") Integer amount);
}
