package com.hmall.item.config;

import com.hmall.item.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.hmall.item.constants.ItemRedisConstants.BLOOM_EXPECTED_INSERTIONS;
import static com.hmall.item.constants.ItemRedisConstants.BLOOM_FPP;
import static com.hmall.item.constants.ItemRedisConstants.ITEM_BLOOM;

/**
 * 启动时把现有商品 id 初始化到布隆过滤器
 * <p>
 * tryInit 只在过滤器不存在时生效：首次启动执行全量加载，之后重启直接跳过，
 * 避免每次启动都做 8 万多次 Redis 写入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemBloomFilterInitializer implements ApplicationRunner {

    private final RedissonClient redissonClient;
    private final ItemMapper itemMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(ITEM_BLOOM);
            boolean created = bloomFilter.tryInit(BLOOM_EXPECTED_INSERTIONS, BLOOM_FPP);
            long existing = bloomFilter.count();
            if (!created && existing > 0) {
                log.info("布隆过滤器已存在且有 {} 个元素，跳过初始化", existing);
                return;
            }
            if (!created) {
                // 配置存在但数据为空：通常是上次初始化被中断，或手工清理时只删掉了位图。
                // 若直接跳过，布隆会一直为空，所有商品都会被误判为不存在
                log.warn("布隆过滤器配置存在但数据为空，强制重新加载商品 id");
            }
            // 首次创建：加载全部商品 id
            long start = System.currentTimeMillis();
            List<Long> ids = itemMapper.selectAllIds();
            // 并行写入：逐条串行写入 8 万条约需 50 秒，期间服务已在监听端口但布隆为空，
            // 会把正常商品误判为不存在；并行下可降到几秒
            ids.parallelStream().forEach(bloomFilter::add);
            log.info("布隆过滤器初始化完成，加载商品 id 共 {} 个，耗时 {} ms",
                    ids.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 布隆初始化失败不应阻断服务启动，但后续查询会退化为直接查缓存和数据库
            log.error("布隆过滤器初始化失败，商品查询将跳过布隆校验", e);
        }
    }
}
