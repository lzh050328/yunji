package com.hmall.trade.service;

import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 黑马程序员
 */
public interface IOrderService extends IService<Order> {

    Long createOrder(OrderFormDTO orderFormDTO);

    void markOrderPaySuccess(Long orderId);

    /**
     * 超时取消订单：仅未支付订单会被关闭，并恢复库存（幂等由调用方保证）
     */
    void cancelOrder(Long orderId);
}
