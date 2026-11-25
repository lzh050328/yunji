package com.hmall.item.service;

import com.hmall.api.item.dto.ItemDTO;
import com.hmall.api.item.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务类
 * </p>
 *
 * @author 黑马程序员
 */
public interface IItemService extends IService<Item> {

    void deductStock(List<OrderDetailDTO> items);

    /**
     * 根据 id 查询商品（布隆过滤器 + Redis 缓存 + 数据库三级）
     *
     * @param id 商品 id
     * @return 商品信息，不存在返回 null
     */
    ItemDTO queryItemById(Long id);

    /**
     * 恢复库存（订单超时取消时调用）
     */
    void restoreStock(List<OrderDetailDTO> items);

    List<ItemDTO> queryItemByIds(Collection<Long> ids);
}
