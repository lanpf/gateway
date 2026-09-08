local key = KEYS[1]
local capacity, refill, requested = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
local t = redis.call('TIME')
local now = t[1] + t[2] / 1000000
local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1]) or capacity
local ts = tonumber(bucket[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - ts) * refill) - requested
local ttl = math.ceil(capacity / refill) * 2
if tokens < 0 then
  redis.call('HMSET', key, 'tokens', 0, 'ts', now)
  redis.call('EXPIRE', key, ttl)
  return {0, 0}
end
redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', key, ttl)
return {1, math.floor(tokens)}
