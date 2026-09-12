-- Atomic EXACT single-token acquisition. Never discard fractional tokens on rejection.
local key = KEYS[1]
local capacity, refill = tonumber(ARGV[1]), tonumber(ARGV[2])
local t = redis.call('TIME')
local now = tonumber(t[1]) + tonumber(t[2]) / 1000000
local bucket = redis.call('HMGET', key, 'tokens', 'ts', 'refill', 'capacity')
local tokens = tonumber(bucket[1])
if bucket[1] and tokens == nil then
  return redis.error_reply('Invalid rate limit bucket state')
end
local ts = tonumber(bucket[2]) or now
local oldRefill = tonumber(bucket[3]) or refill
local oldCapacity = tonumber(bucket[4]) or capacity
if tokens == nil then
  tokens = capacity
else
  -- Settle elapsed time under the previous configuration before applying the new cap.
  tokens = math.min(capacity, math.min(oldCapacity, tokens + math.max(0, now - ts) * oldRefill))
end
local allowed, retryMillis = 0, 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retryMillis = math.max(1, math.ceil(((1 - tokens) / refill + math.max(0, ts - now)) * 1000))
end
-- Do not refill the same interval twice when the wall clock moves backwards.
local observedAt = math.max(now, ts)
redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(observedAt),
           'refill', tostring(refill), 'capacity', tostring(capacity))
-- Idle buckets expire only after enough time to be fully replenished.
local ttlMillis = math.max(1000, math.ceil(capacity / refill * 2000 + math.max(0, ts - now) * 1000))
redis.call('PEXPIRE', key, ttlMillis)
return {allowed, math.floor(tokens), retryMillis}
