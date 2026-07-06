--[[
  Sliding Window Rate Limiter (weighted approximation)
  -----------------------------------------------------
  KEYS[1]  = current window counter key  (e.g. rl:bucket:user:123:curr)
  KEYS[2]  = previous window counter key (e.g. rl:bucket:user:123:prev)
  ARGV[1]  = limit          (max requests in any rolling window)
  ARGV[2]  = windowSeconds
  ARGV[3]  = currentEpoch

  Returns: { allowed, remaining }

  Algorithm
  ---------
  Rather than tracking every request timestamp (expensive with high traffic),
  we keep two counters: the current window and the previous window.

  The weight of the previous window decreases linearly as we move through
  the current window:

    elapsed  = currentEpoch % windowSeconds       (seconds into current window)
    weight   = (windowSeconds - elapsed) / windowSeconds  (1.0 → 0.0)
    estimate = floor(prevCount * weight) + currCount

  If estimate < limit  →  increment currCount and allow.
  Otherwise           →  deny.

  At window boundaries (elapsed == 0) the Lua script rotates:
    prev = curr, curr = 0

  This gives a smooth sliding rate with only two integer counters in Redis
  instead of a sorted set of timestamps.
--]]

local currKey       = KEYS[1]
local prevKey       = KEYS[2]
local limit         = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local now           = tonumber(ARGV[3])

local elapsed    = now % windowSeconds
local windowSlot = math.floor(now / windowSeconds)

-- Read both counters (GET returns false/nil if key missing, coerce to 0)
local currCount = tonumber(redis.call('GET', currKey)) or 0
local prevCount = tonumber(redis.call('GET', prevKey)) or 0

-- Rotate counters at window boundary
if elapsed == 0 then
    -- New window just started: current becomes previous, reset current
    redis.call('SET',    prevKey, currCount)
    redis.call('EXPIRE', prevKey, windowSeconds * 2)
    redis.call('SET',    currKey, 0)
    redis.call('EXPIRE', currKey, windowSeconds * 2)
    prevCount = currCount
    currCount = 0
end

-- Weight: how much of the previous window still "counts"
local weight   = (windowSeconds - elapsed) / windowSeconds
local estimate = math.floor(prevCount * weight) + currCount

if estimate < limit then
    -- Allow: increment current counter
    local newCount = redis.call('INCR', currKey)
    if newCount == 1 then
        redis.call('EXPIRE', currKey, windowSeconds * 2)
    end
    local remaining = limit - estimate - 1
    return { 1, remaining < 0 and 0 or remaining }
else
    return { 0, 0 }
end
