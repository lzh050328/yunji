package com.hmall.item.constants;

/**
 * 商品服务 Redis 相关常量
 */
public interface ItemRedisConstants {

    /**
     * 商品 id 布隆过滤器名称
     */
    String ITEM_BLOOM = "item:bloom-filter";

    /**
     * 商品详情缓存 key 前缀
     */
    String ITEM_CACHE_PREFIX = "item:cache:";

    /**
     * 商品缓存过期时间（分钟）
     */
    long CACHE_TTL_MINUTES = 30L;

    /**
     * 布隆过滤器预期元素数量：现有商品约 8.8 万，留足余量
     */
    long BLOOM_EXPECTED_INSERTIONS = 200_000L;

    /**
     * 布隆过滤器误判率
     */
    double BLOOM_FPP = 0.01;
}
