package com.hmall.trade.listener;

import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.PaySuccessMessage;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息监听
 * <p>
 * 交换机、队列、绑定关系全部通过 @RabbitListener 注解声明，不使用配置类方式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaySuccessListener {

    private final IOrderService orderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.PAY_SUCCESS_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.PAY_DIRECT_EXCHANGE,
                    type = ExchangeTypes.DIRECT, durable = "true"),
            key = MqConstants.PAY_SUCCESS_ROUTING_KEY
    ))
    public void listenPaySuccess(PaySuccessMessage message) {
        if (message == null || message.getOrderId() == null) {
            log.warn("收到支付成功消息但订单号为空，已忽略：{}", message);
            return;
        }
        log.info("收到支付成功消息，订单号：{}，用户：{}", message.getOrderId(), message.getUserId());
        orderService.markOrderPaySuccess(message.getOrderId());
        log.info("订单状态已更新为已支付，订单号：{}", message.getOrderId());
    }
}
