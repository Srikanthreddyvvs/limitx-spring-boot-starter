--[[
  Fixed Window Rate Limiter
  -------------------------
  KEYS[1]  = window-scoped bucket key  (e.g. rl:bucket:user:123:28333334)
  ARGV[1]  = limit          (max requests per window)
  ARGV[2]  = windowSeconds  (window size in seconds)
  ARGV[3]  = currentEpoch   (current Unix timestamp in seconds)

  Returns: { allowed, remaining }
    allowed   = 1 if request is permitted, 0 if denied
    remaining = tokens left after this request (0 if denied)

  Why Lua?
  The INCR + check + expire sequence must be atomic. Without it, two
  concurrent requests could both read count=limit-1, both pass the check,
  and both increment — allowing limit+1 requests through. Lua scripts
  execute as a single Redis command (no interleaving).
--]]

local key           = KEYS[1]
local limit         = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])

-- Atomically increment the counter.
-- INCR creates the key at 0 then increments, so the first request gets 1.
local current = redis.call('INCR', key)

-- Set the TTL only on the very first request in this window (when count == 1).
-- This avoids resetting the expiry on every request, which would extend the
-- window indefinitely under constant traffic.
if current == 1 then
    redis.call('EXPIRE', key, windowSeconds)
end

if current <= limit then
    return { 1, limit - current }   -- allowed, remaining
else
    return { 0, 0 }                 -- denied
end
