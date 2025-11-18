-- 1.参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]

-- 2.有关数据key
-- 2.1 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherId

--3.脚本业务
-- 3.1 判断库存是否充足
local stock = redis.call('get', stockKey)
if (stock == false) then
    return 1
end
if (tonumber(stock) <= 0) then
    return 1
end

--3.2判断用户是否下单
if(redis.call('sismember', orderKey, userId) == 1) then
    --用户已经下单
    return 2
end
--3.3扣减库存
redis.call('incrby', stockKey, -1)
--下单
redis.call('sadd', orderKey, userId)
return 0