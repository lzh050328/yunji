package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.cart.CartClient;
import com.hmall.api.item.ItemClient;
import com.hmall.api.item.dto.ItemDTO;
import com.hmall.api.item.dto.OrderDetailDTO;
import com.hmall.api.pay.PayClient;
import com.hmall.common.constants.MqConstants;
import com.hmall.common.domain.mq.OrderCloseMessage;
import com.hmall.common.domain.mq.OrderCreateMessage;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 黑马程序员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final CartClient cartClient;
    /**
     * RabbitMQ 模板：下单成功后发送消息，由购物车服务异步清理
     */
    private final RabbitTemplate rabbitTemplate;
    /**
     * 支付服务客户端：取消订单前实时核对支付状态
     */
    private final PayClient payClient;

    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        var order = new Order();
        // 1.1.查询商品
        var detailDTOS = orderFormDTO.getDetails();
        // 1.1.1.参数校验：订单明细不能为空
        if (detailDTOS == null || detailDTOS.isEmpty()) {
            throw new BadRequestException("订单明细不能为空");
        }
        // 1.2.获取商品id和数量的Map
        var itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        var itemIds = itemNumMap.keySet();
        // 1.3.查询商品（远程调用 item-service）
        var items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        var total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.扣减库存（远程调用 item-service）
        // 必须放在清理购物车之前：库存不足是最常见的失败场景，此时购物车还没动，
        // 用户可以直接重试；若先清购物车再扣库存，扣减失败会导致购物车被白清空
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Throwable e) {
            // Sentinel 的 fallback 抛出的异常会被包装成 Error（AssertionError），catch Exception 捕获不到
            log.error("扣减库存失败，订单号：{}", order.getId(), e);
            throw new BadRequestException("库存不足，下单失败");
        }

        // 4.发送下单成功消息，由购物车服务异步清理对应商品（替代原 OpenFeign 同步调用）
        // 消息体必须自带 userId：购物车服务消费时没有 HTTP 请求上下文，UserContext 为空
        try {
            var message = new OrderCreateMessage();
            message.setUserId(UserContext.getUser());
            message.setItems(detailDTOS.stream().map(dto -> {
                var item = new OrderCreateMessage.OrderItem();
                item.setItemId(dto.getItemId());
                item.setNum(dto.getNum());
                return item;
            }).collect(Collectors.toList()));

            rabbitTemplate.convertAndSend(
                    MqConstants.TRADE_TOPIC_EXCHANGE,
                    MqConstants.ORDER_CREATE_ROUTING_KEY,
                    message
            );
            log.info("下单成功消息已发送，交换机：{}，路由键：{}，用户：{}，商品数：{}",
                    MqConstants.TRADE_TOPIC_EXCHANGE, MqConstants.ORDER_CREATE_ROUTING_KEY,
                    message.getUserId(), message.getItems().size());
        } catch (Throwable e) {
            // 库存已扣、订单已落库，清购物车属附带动作，消息发送失败不能让下单失败
            log.error("下单成功但清理购物车消息发送失败，订单号：{}，商品：{}", order.getId(), itemIds, e);
        }

        // 5.发送延迟消息：30分30秒后检查订单是否仍未支付，未支付则自动关闭
        try {
            var closeMessage = new OrderCloseMessage(order.getId());
            var messageId = UUID.randomUUID().toString();
            rabbitTemplate.convertAndSend(
                    MqConstants.TRADE_DELAY_EXCHANGE,
                    MqConstants.ORDER_CLOSE_ROUTING_KEY,
                    closeMessage,
                    msg -> {
                        // x-delay 头由延迟插件读取，单位毫秒
                        msg.getMessageProperties().setDelay((int) MqConstants.ORDER_TIMEOUT_DELAY_MS);
                        // 消息id用于消费端幂等
                        msg.getMessageProperties().setMessageId(messageId);
                        return msg;
                    }
            );
            log.info("订单超时检查消息已发送，订单号：{}，延迟：{}秒，消息id：{}",
                    order.getId(), MqConstants.ORDER_TIMEOUT_DELAY_MS / 1000, messageId);
        } catch (Throwable e) {
            // 订单已创建成功，延迟消息发送失败只影响超时取消，不能让下单失败
            log.error("订单超时检查消息发送失败，订单号：{}", order.getId(), e);
        }

        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        var order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        // 1.查询订单
        var order = getById(orderId);
        if (order == null) {
            log.warn("订单不存在，无需取消，订单号：{}", orderId);
            return;
        }

        // 2.状态判断：只有待付款（status=1）才能取消
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            log.info("订单 {} 当前状态为 {}，不是待付款，跳过取消", orderId, order.getStatus());
            return;
        }

        // 3.实时核对支付状态，防止用户在临界点已经付款
        try {
            var payStatus = payClient.queryStatus(orderId);
            if (Integer.valueOf(3).equals(payStatus)) {
                log.info("订单 {} 支付单已成功，不予取消", orderId);
                return;
            }
        } catch (Throwable e) {
            // 查不到支付状态时保守处理：宁可不取消，也不能误关已付款的订单
            log.error("查询订单 {} 支付状态失败，本轮不取消，等待人工核对", orderId, e);
            return;
        }

        // 4.乐观锁更新为交易关闭，status 仍为 1 才生效，避免与支付并发覆盖
        var updated = lambdaUpdate()
                .set(Order::getStatus, 5)
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
        if (!updated) {
            log.info("订单 {} 状态已被并发修改，本次取消放弃", orderId);
            return;
        }

        // 5.恢复库存
        var details = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId)
                .list();
        if (CollUtils.isNotEmpty(details)) {
            var detailDTOS = details.stream().map(d -> {
                var dto = new OrderDetailDTO();
                dto.setItemId(d.getItemId());
                dto.setNum(d.getNum());
                return dto;
            }).toList();
            itemClient.restoreStock(detailDTOS);
        }
        log.info("订单 {} 超时取消成功，已恢复 {} 个商品的库存", orderId, details.size());
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        var details = new ArrayList<OrderDetail>(items.size());
        for (ItemDTO item : items) {
            var detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
