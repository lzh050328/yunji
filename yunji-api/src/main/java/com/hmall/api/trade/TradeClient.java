package com.hmall.api.trade;

import com.hmall.api.config.FeignCommonConfig;
import com.hmall.api.trade.dto.OrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 交易服务远程调用客户端
 * 对应 trade-service 的订单查询、标记订单已支付接口
 */
@FeignClient(name = "trade-service", path = "/orders", configuration = FeignCommonConfig.class)
public interface TradeClient {

    @GetMapping("{id}")
    OrderVO queryOrderById(@PathVariable("id") Long orderId);

    @PutMapping("/{orderId}")
    void markOrderPaySuccess(@PathVariable("orderId") Long orderId);
}
