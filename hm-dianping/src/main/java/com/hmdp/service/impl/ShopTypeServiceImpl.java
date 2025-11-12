package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 查询所有商铺类型
     * @return
     */
    @Override
    public Result queryTypeList() {
        //1.从Reids中查询分类缓存
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.判断缓存是否存在
        if(shopTypeList != null && shopTypeList.size() > 0  ){
            //3.存在，直接返回
            List<ShopType> list = new ArrayList<>();
            for(String type : shopTypeList){
                list.add(JSONUtil.toBean(type, ShopType.class));
            }
            return Result.ok(list);
        }
        //4.不存在，查询数据库
        List<ShopType> typeList = this
                .query().orderByAsc("sort").list();
        //5.数据库不存在，返回错误
        if(typeList == null){
            return Result.fail("店铺类型不存在");
        }
        //6.存在，写入Redis
        List<String> list = new ArrayList<>();
        for(ShopType type : typeList){
            list.add(JSONUtil.toJsonStr(type));
        }
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, list);

        //7.返回
        return Result.ok(typeList);
    }
}
