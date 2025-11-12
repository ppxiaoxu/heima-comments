package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    // 锁的key
    private String name;

    private StringRedisTemplate stringRedisTemplate;
    // 锁的前缀
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    //脚本初始化
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }


//    /**
//     * 释放锁
//     */
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        //判断表示是否一致
//        if(id.equals(threadId)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }

    /**
     * 释放锁（执行lua脚本）
     */
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name), ID_PREFIX + Thread.currentThread().getId());
    }
}
