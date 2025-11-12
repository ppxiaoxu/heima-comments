package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 秒杀券
     * @param voucherId
     * @return
     */
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        // 1.查询优惠券

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }
        // 3.判断秒杀是否结束

        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            // 秒杀结束
            return Result.fail("秒杀已结束");
        }
        // 4.判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        // 7.返回订单号
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        // 获取锁
        boolean isLock = lock.tryLock(5L);
        if(!isLock){
            // 获取锁失败
            return Result.fail("该优惠券不允许重复下单");
        }

        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        }finally {
                //释放锁
                lock.unlock();

        }

    }

    /**
     * 创建优惠券订单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //一人一单
        Long userId = UserHolder.getUser().getId();


            //查询订单
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if(count > 0){
                //用户已购买
                return Result.fail("已购买,不能再次购买");
            }

            // 5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();

            if(!success){
                return Result.fail("库存不足");
            }


            // 6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);




    }
}
