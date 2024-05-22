--参数列表
local voucherId  = ARGV[1]
local userID = ARGV[2]
--数据key
local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

if tonumber(redis.call('get',stockKey)) <= 0 then
    return 1
end

if redis.call('sismember',orderKey,userID) == 1 then
    return 2
end

-- 扣减库存和添加用户
redis.call('sadd',orderKey,userID)
redis.call('decrby',stockKey,1)
return 0

-- Lua 脚本
-- KEYS[1] 是库存的键，比如 "stock:vid:7"
-- KEYS[2] 是订单集合的键，比如 "order:vid:7"
-- ARGV[1] 是要减去的库存数量
-- ARGV[2] 是用户ID

-- 检查用户是否已经下过单
--local isUserAlreadyOrdered = redis.call('sismember', KEYS[2], ARGV[2])
--if isUserAlreadyOrdered == 1 then
--    -- 用户已经下单，不再进行操作
--    return 2 -- 可以返回一个特定的数字表示这种状态
--end
--
---- 检查库存是否足够
--local stock = tonumber(redis.call('get', KEYS[1]))
--if stock == nil or stock < tonumber(ARGV[1]) then
--    -- 库存不足
--    return 0
--else
--    -- 减少库存
--    redis.call('decrby', KEYS[1], ARGV[1])
--    -- 添加用户ID到订单集合
--    redis.call('sadd', KEYS[2], ARGV[2])
--    return 1 -- 库存减少并添加订单成功
--end



