package com.hmall.points.listener;

import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.PaySuccessMessage;
import com.hmall.points.service.IPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 支付成功消息监听：给付款用户加积分
 * <p>
 * 使用独立队列 pay.points.queue，与交易服务的 pay.success.queue 绑定同一个 routingKey，
 * direct 交换机会同时投递到两个队列，两个消费者各收一份，互不抢夺
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaySuccessListener {

    private final IPointsService pointsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.PAY_POINTS_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.PAY_DIRECT_EXCHANGE,
                    type = ExchangeTypes.DIRECT, durable = "true"),
            key = MqConstants.PAY_SUCCESS_ROUTING_KEY
    ))
    public void listenPaySuccess(PaySuccessMessage message) {
        if (message == null || message.getUserId() == null || message.getOrderId() == null) {
            log.warn("收到支付成功消息但内容不完整，已忽略：{}", message);
            return;
        }
        log.info("积分服务收到支付成功消息，用户：{}，订单：{}", message.getUserId(), message.getOrderId());
        pointsService.addPointsForPaySuccess(message.getUserId(), message.getOrderId());
    }
}
