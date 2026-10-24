-- KEYS[1]: activity quota key (community:activity:quota:{id})
-- KEYS[2]: activity signed users set (community:activity:users:{id})
-- ARGV[1]: userId

-- 1. 检查用户是否已报名
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return -1
end

-- 2. 检查剩余名额
local quota = tonumber(redis.call('get', KEYS[1]))
if not quota or quota <= 0 then
    return 0
end

-- 3. 原子扣减名额并记录用户
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])

return 1
