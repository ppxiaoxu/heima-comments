package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutext(id);

//        //逻辑过期解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        if(shop == null){
//            return Result.fail("店铺不存在");
//        }

        //返回
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//
//    /**
//     * 缓存击穿-逻辑过期
//     * @param id
//     * @return
//     */
//    public Shop queryWithLogicExpire(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis查店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //3.存在直接返回
//           return null;
//        }
//        //4.命中，需要把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //5.1未过期，返回店铺信息
//            return shop;
//        }
//        //5.2已过期，缓存重建
//        //6.缓存重建
//        //6.1获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //6.2判断获取锁成功
//        if(isLock){
//            if(expireTime.isBefore(LocalDateTime.now())){
//                //6.3成功，开启独立线程，实现缓存重建
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    try {
//                        this.saveShop2Redis(id, 20L);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        //释放锁
//                        unLock(lockKey);
//                    }
//                });
//            }
//        }
//
//
//        //6.4失败，返回商品信息（过期）
//        return shop;
//    }

//    /**
//     * 缓存击穿-互斥锁
//     * @param id
//     * @return
//     */
//    public Shop queryWithMutext(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis查店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        //判断命中的是否为空值
//        if(shopJson != null){
//            //返回错误
//            return null;
//        }
//
//        //实现缓存重建
//        //4.1.获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //4.2 判断是否获取锁成功
//            if(!isLock){
//                //获取锁失败，返回错误或者重试
//                Thread.sleep(50);
//                //递归
//                queryWithMutext(id);
//            }
//
//            //4.4.获取锁成功，根据id查询数据库
//            shop = getById(id);
//            //5.数据库不存在，返回错误
//            if(shop == null){
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //6.存在，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //7.释放互斥锁
//            unLock(lockKey);
//        }
//        //8.返回
//        return shop;
//    }

//    /**
//     * 缓存穿透
//     * @param id
//     * @return
//     */
//    public Shop queryWithPassThrough(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis查店铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        //判断命中的是否为空值
//        if(shopJson != null){
//            //返回错误
//            return null;
//        }
//
//        //4.不存在，查数据库
//        Shop shop = getById(id);
//        //5.数据库不存在，返回错误
//        if(shop == null){
//            //将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //6.存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }

//    /**
//     * 尝试获取锁
//     * @param key
//     * @return
//     */
//    private boolean tryLock(String key){
//        // 占分布式锁
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }

//    /**
//     * 释放锁
//     * @param key
//     */
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }

//    /**
//     * 保存店铺信息到redis
//     * @param id
//     * @param expireSeconds
//     */
//    public void saveShop2Redis(Long id, Long expireSeconds){
//        //1.查询店铺数据
//        Shop shop = getById(id);
//        //2.封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3.写入redis
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}















