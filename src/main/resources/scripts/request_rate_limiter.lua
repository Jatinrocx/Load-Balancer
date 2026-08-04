-- Redis Token Bucket Rate Limiter Lua Script (Per-Minute Window)
-- KEYS[1]: rate_limiter:<key>:tokens
-- KEYS[2]: rate_limiter:<key>:timestamp
-- ARGV[1]: bucket capacity / max requests per window (e.g. 10)
-- ARGV[2]: window period in seconds (e.g. 60)
-- ARGV[3]: current timestamp in seconds
-- ARGV[4]: requested tokens (default 1)

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]

local capacity = tonumber(ARGV[1])
local period = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local ttl = period * 2

local last_tokens = tonumber(redis.call('get', tokens_key))
if last_tokens == nil then
    last_tokens = capacity
end

local last_refreshed = tonumber(redis.call('get', timestamp_key))
if last_refreshed == nil then
    last_refreshed = now
end

-- Calculate refilled tokens for time elapsed in seconds
local delta = math.max(0, now - last_refreshed)
local tokens_to_add = (delta * capacity) / period
local tokens = math.min(capacity, last_tokens + tokens_to_add)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

-- Save updated token count and refresh timestamp atomically with TTL
redis.call('setex', tokens_key, ttl, tokens)
redis.call('setex', timestamp_key, ttl, now)

-- Returns: { allowed (1 or 0), remaining_tokens, bucket_capacity }
return { allowed, math.floor(tokens), capacity }
