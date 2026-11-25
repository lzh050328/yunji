package com.hmall.cart.listener;

import com.hmall.cart.service.ICartService;
import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.OrderCreateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 下单成功消息监听：清理购物车中已下单的商品
 * <p>
 * 交换机、队列、绑定关系全部通过 @RabbitListener 注解声明，不使用配置类方式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateListener {

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.CART_CLEAR_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.TRADE_TOPIC_EXCHANGE,
                    type = ExchangeTypes.TOPIC, durable = "true"),
            key = MqConstants.ORDER_CREATE_ROUTING_KEY
    ))
    public void listenOrderCreate(OrderCreateMessage message) {
        if (message == null || message.getUserId() == null || message.getItems() == null) {
            log.warn("收到下单成功消息但内容不完整，已忽略：{}", message);
            return;
        }
        // 消费消息时没有 HTTP 上下文，用户信息只能从消息体里取
        List<Long> itemIds = message.getItems().stream()
                .map(OrderCreateMessage.OrderItem::getItemId)
                .toList();

        int rows = cartService.removeByUserIdAndItemIds(message.getUserId(), itemIds);
        log.info("收到下单成功消息，用户：{}，商品：{}，清理购物车 {} 条", message.getUserId(), itemIds, rows);
    }
}
