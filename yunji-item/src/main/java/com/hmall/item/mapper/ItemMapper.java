package com.hmall.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.api.item.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 商品表 Mapper 接口
 * </p>
 *
 * @author 黑马程序员
 */
public interface ItemMapper extends BaseMapper<Item> {

    @Update("UPDATE item SET stock = stock - #{num} WHERE id = #{itemId} and stock >= #{num}")
    int updateStock(OrderDetailDTO orderDetail);

    /**
     * 恢复库存：订单取消时把已扣的库存加回来
     */
    @Update("UPDATE item SET stock = stock + #{num} WHERE id = #{itemId}")
    int restoreStock(OrderDetailDTO orderDetail);

    /**
     * 查询全部商品 id，用于初始化布隆过滤器
     */
    @Select("SELECT id FROM item")
    List<Long> selectAllIds();
}
