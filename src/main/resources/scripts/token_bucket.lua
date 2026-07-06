--[[
  Token Bucket Rate Limiter
  -------------------------
  KEYS[1]  = bucket key  (e.g. rl:bucket:user:123)
  ARGV[1]  = limit          (max tokens / burst capacity)
  ARGV[2]  = refillTokens   (tokens added per refill interval)
  ARGV[3]  = windowSeconds  (refill interval in seconds)
  ARGV[4]  = currentEpoch

  Returns: { allowed, remainingTokens, nextRefillEpoch }

  State stored as a Redis hash under KEYS[1]:
    tokens      -> current token count  (float stored as string)
    lastRefill  -> epoch second of last refill

  Algorithm
  ---------
  1. Read tokens + lastRefill from the hash.
  2. Compute how many full refill intervals have passed since lastRefill.
  3. Add (intervals * refillTokens) to tokens, capped at limit.
  4. If tokens >= 1: consume one token, allow.
     Else: deny.
  5. Write updated tokens + lastRefill back atomically.

  The TTL on the hash is refreshed to (2 * windowSeconds) on every request
  so idle buckets eventually expire and don't consume memory forever.
--]]

local key           = KEYS[1]
local limit         = tonumber(ARGV[1])
local refillTokens  = tonumber(ARGV[2])
local windowSeconds = tonumber(ARGV[3])
local now           = tonumber(ARGV[4])

-- Read current state from hash; default to full bucket on first access
local stored     = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens     = tonumber(stored[1]) or limit
local lastRefill = tonumber(stored[2]) or now

-- Calculate how many complete refill intervals have elapsed
local intervals  = math.floor((now - lastRefill) / windowSeconds)
local newTokens  = math.min(limit, tokens + (intervals * refillTokens))
local newRefill  = lastRefill + (intervals * windowSeconds)

-- Next refill epoch (used for Retry-After header)
local nextRefill = newRefill + windowSeconds

local allowed    = 0
local remaining  = 0

if newTokens >= 1 then
    allowed   = 1
    newTokens = newTokens - 1
    remaining = math.floor(newTokens)
end

-- Persist updated state; expire idle buckets to keep Redis memory bounded
redis.call('HMSET', key,
    'tokens',     tostring(newTokens),
    'lastRefill', tostring(newRefill))
redis.call('EXPIRE', key, windowSeconds * 2)

return { allowed, remaining, nextRefill }
