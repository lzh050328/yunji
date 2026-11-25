package com.hmall.api.item;

import com.hmall.api.item.dto.ItemDTO;
import com.hmall.api.item.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.CollUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 商品服务 Feign 客户端的降级工厂
 * 远程调用失败（超时、服务不可用、熔断）时，返回兜底数据并记录错误日志
 */
@Slf4j
@Component
public class ItemClientFallbackFactory implements FallbackFactory<ItemClient> {

    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                // 有返回值的方法：返回空集合兜底
                log.error("查询商品失败，商品id：{}", ids, cause);
                return CollUtils.emptyList();
            }

            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                // 无返回值的方法：记录错误日志
                log.error("扣减商品库存失败，参数：{}", items, cause);
                // 扣库存不允许降级放行：否则会出现"订单创建成功但库存没扣"的超卖，必须向上抛出由调用方感知
                throw new BizIllegalException("扣减商品库存失败，请稍后重试");
            }

            @Override
            public void restoreStock(List<OrderDetailDTO> items) {
                log.error("恢复商品库存失败，参数：{}", items, cause);
                // 恢复库存失败要抛出让消息重试，否则订单已关闭、库存却没还回去
                throw new BizIllegalException("恢复商品库存失败，请稍后重试");
            }
        };
    }
}
