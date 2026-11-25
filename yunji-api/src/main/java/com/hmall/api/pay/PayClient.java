package com.hmall.api.pay;

import com.hmall.api.config.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 支付服务远程调用客户端
 */
@FeignClient(name = "pay-service", path = "/pay-orders",
        configuration = FeignCommonConfig.class)
public interface PayClient {

    /**
     * 查询某笔业务订单的支付单状态
     *
     * @param bizOrderNo 业务订单号
     * @return 0-未提交 1-待支付 2-已关闭 3-支付成功；null 表示还没有支付单
     */
    @GetMapping("/status/{bizOrderNo}")
    Integer queryStatus(@PathVariable("bizOrderNo") Long bizOrderNo);
}
