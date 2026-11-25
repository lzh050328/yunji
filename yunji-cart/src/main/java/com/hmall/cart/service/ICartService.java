package com.hmall.cart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 订单详情表 服务类
 * </p>
 *
 * @author 黑马程序员
 */
public interface ICartService extends IService<Cart> {

    void addItem2Cart(CartFormDTO cartFormDTO);

    List<CartVO> queryMyCarts();

    void removeByItemIds(Collection<Long> itemIds);

    /**
     * 按用户id和商品id批量删除购物车（MQ 消费专用）
     * <p>
     * 消费消息时没有 HTTP 上下文，无法从 UserContext 取用户id，必须显式传入
     *
     * @param userId  用户id
     * @param itemIds 商品id集合
     * @return 删除的条数
     */
    int removeByUserIdAndItemIds(Long userId, Collection<Long> itemIds);
}
