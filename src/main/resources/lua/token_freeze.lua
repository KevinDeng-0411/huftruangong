-- KEYS[1]: used_key (已使用的额度)
-- KEYS[2]: frozen_key (当前冻结的额度)
-- ARGV[1]: limit (每日总限额)
-- ARGV[2]: estimate (本次请求预估额度)

local used = tonumber(redis.call('GET', KEYS[1]) or '0')
local frozen = tonumber(redis.call('GET', KEYS[2]) or '0')
local limit = tonumber(ARGV[1])
local estimate = tonumber(ARGV[2])

-- 核心公式：已用 + 已冻结 + 本次预估 > 限额
if (used + frozen + estimate) > limit then
    return -1 -- 拒绝
end

-- 允许：增加冻结额度
redis.call('INCRBY', KEYS[2], estimate)
-- 设置过期时间（例如 24 小时），防止死锁
redis.call('EXPIRE', KEYS[2], 86400)
redis.call('EXPIRE', KEYS[1], 86400)

return 1 -- 通过