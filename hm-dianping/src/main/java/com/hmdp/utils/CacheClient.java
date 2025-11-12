package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 缓存数据
     * @param key 键
     * @param value 值
     * @param time 时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 缓存逻辑过期数据
     * @param key 键
     * @param value 值
     * @param time 时间
     * @param unit 时间单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从redis查店铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在直接返回
            return JSONUtil.toBean(json, type);
        }

        //判断命中的是否为空值
        if(json != null){
            //返回错误
            return null;
        }

        //4.不存在，查数据库
        R r = dbFallback.apply(id);
        //5.数据库不存在，返回错误
        if(r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        this.set(key, r, time, unit);
        //7.返回
        return r;
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿-逻辑过期
     * @param id
     * @return
     */
    public <R,  ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
            //3.存在直接返回
            return null;
        }
        //4.命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期，返回店铺信息
            return r;
        }
        //5.2已过期，缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2判断获取锁成功
        if(isLock){
            if(expireTime.isBefore(LocalDateTime.now())){
                //6.3成功，开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        //缓存重建
                        R r1 = dbFallback.apply(id);
                        this.setWithLogicExpire(key, r1, time, unit);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }
        }
        //6.4失败，返回商品信息（过期）
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        // 占分布式锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


}
