package com.hmall.cart.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.item.ItemClient;
import com.hmall.api.item.dto.ItemDTO;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;
import com.hmall.cart.mapper.CartMapper;
import com.hmall.cart.service.ICartService;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 黑马程序员
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    /**
     * 商品服务远程调用客户端，用于填充购物车条目中的商品最新价格、状态与库存
     */
    @Autowired
    private ItemClient itemClient;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户（网关校验 token 后通过 user-info 头传入，拦截器已还原到 UserContext）
        var userId = UserContext.getUser();

        // 2.判断是否已经存在
        if(checkItemExists(cartFormDTO.getItemId(), userId)){
            // 2.1.存在，则累加数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId, cartFormDTO.getNum());
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.查询商品信息，以服务端数据为准（前端可能不传，也不能信任前端传的价格）
        var item = getItemById(cartFormDTO.getItemId());

        // 4.新增购物车条目
        // 4.1.转换PO
        var cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 4.2.保存当前用户
        cart.setUserId(userId);
        // 4.3.用服务端商品信息覆盖名称、价格、图片，防止前端篡改
        cart.setName(item.getName());
        cart.setPrice(item.getPrice());
        cart.setImage(item.getImage());
        // 4.4.保存到数据库
        save(cart);
    }

    /**
     * 查询商品，不存在则抛出业务异常
     */
    private ItemDTO getItemById(Long itemId) {
        var items = itemClient.queryItemByIds(List.of(itemId));
        if (CollUtils.isEmpty(items)) {
            throw new BadRequestException("商品不存在");
        }
        return items.get(0);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        var carts = lambdaQuery().eq(Cart::getUserId, UserContext.getUser()).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        var vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }

    private void handleCartItems(List<CartVO> vos) {
        // 商品信息填充依赖 item-service，当前已拆为独立服务，本地无 IItemService 可用。
        // 待 hm-api 模块建好后，通过 OpenFeign 远程调用 item-service 的批量查询接口恢复以下逻辑：
        //
        var itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        // 通过Feign来获取商品最新价格/状态/库存
        var items = this.itemClient.queryItemByIds(itemIds);
        if (CollUtils.isEmpty(items)) {
            return;
        }
        var itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        for (var v : vos) {
            var item = itemMap.get(v.getItemId());
            if (item == null) {
                continue;
            }
            v.setNewPrice(item.getPrice().intValue());
            v.setStatus(item.getStatus().intValue());
            v.setStock(item.getStock().intValue());
         }
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        var queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
    }

    @Override
    public int removeByUserIdAndItemIds(Long userId, Collection<Long> itemIds) {
        if (userId == null || CollUtils.isEmpty(itemIds)) {
            return 0;
        }
        var queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, userId)
                .in(Cart::getItemId, itemIds);
        // remove 返回是否成功，这里改用 baseMapper.delete 拿到影响行数，便于日志统计
        return baseMapper.delete(queryWrapper);
    }

    private void checkCartsFull(Long userId) {
        var count = lambdaQuery().eq(Cart::getUserId, userId).count();
        if (count >= 10) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", 10));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        var count = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .count();
        return count > 0;
    }
}
