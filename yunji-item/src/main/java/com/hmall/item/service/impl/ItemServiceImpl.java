package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.item.dto.ItemDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.api.item.dto.OrderDetailDTO;
import com.hmall.item.domain.po.Item;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmall.item.constants.ItemRedisConstants.*;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 黑马程序员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public ItemDTO queryItemById(Long id) {
        // 1.布隆过滤器：判定一定不存在，直接返回，避免穿透到缓存和数据库
        var bloomFilter = redissonClient.<Long>getBloomFilter(ITEM_BLOOM);
        if (!bloomFilter.contains(id)) {
            log.info("商品{}在布隆过滤器中不存在，直接返回", id);
            return null;
        }

        // 2.查 Redis 缓存
        var cacheKey = ITEM_CACHE_PREFIX + id;
        var cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            log.info("商品{}命中缓存", id);
            return JSONUtil.toBean(cached, ItemDTO.class);
        }

        // 3.缓存未命中，查数据库
        var item = getById(id);
        if (item == null) {
            log.info("商品{}在数据库中不存在", id);
            return null;
        }

        // 4.回写缓存，设置过期时间，防止缓存雪崩时集体失效
        var dto = BeanUtils.copyBean(item, ItemDTO.class);
        stringRedisTemplate.opsForValue()
                .set(cacheKey, JSONUtil.toJsonStr(dto), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.info("商品{}已写入缓存", id);
        return dto;
    }

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            var i = baseMapper.updateStock(item);
            if(i<=0){
                throw new BizIllegalException("库存不足！");
            }
        }
    }

    @Override
    public void restoreStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            baseMapper.restoreStock(item);
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }
}
