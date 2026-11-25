package com.hmall.api.item;

import com.hmall.api.config.FeignCommonConfig;
import com.hmall.api.item.dto.ItemDTO;
import com.hmall.api.item.dto.OrderDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/**
 * 商品服务远程调用客户端
 * 对应 item-service 的批量查询商品、批量扣减库存接口
 */
@FeignClient(name = "item-service", path = "/items",
        fallbackFactory = ItemClientFallbackFactory.class,
        configuration = FeignCommonConfig.class)
public interface ItemClient {

    @GetMapping
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);

    @PutMapping("/stock/deduct")
    void deductStock(@RequestBody List<OrderDetailDTO> items);

    /**
     * 批量恢复库存（订单超时取消时调用）
     */
    @PutMapping("/stock/restore")
    void restoreStock(@RequestBody List<OrderDetailDTO> items);
}
