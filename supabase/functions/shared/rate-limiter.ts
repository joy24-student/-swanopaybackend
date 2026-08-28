// Reusable Deno Rate Limiter Middleware for Supabase Edge Functions
// Uses in-memory caching mapping requests against client IP coordinates or Merchant tokens.

interface RateLimitInfo {
  count: number;
  resetTime: number;
}

const cache = new Map<string, RateLimitInfo>();

// Prune expired cache keys periodically (every 10 minutes)
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of cache.entries()) {
    if (now > value.resetTime) {
      cache.delete(key);
    }
  }
}, 10 * 60 * 1000);

export function rateLimit(
  key: string,
  limit = 60, // Max 30 requests per minute
  windowMs = 60000 // 1 minute window
) {
  const now = Date.now();
  const info = cache.get(key);

  if (!info) {
    // Fresh rate limit bucket
    const resetTime = now + windowMs;
    cache.set(key, { count: 1, resetTime });
    return {
      allowed: true,
      limit,
      remaining: limit - 1,
      resetTime
    };
  }

  if (now > info.resetTime) {
    // Window expired, reset bucket
    const resetTime = now + windowMs;
    cache.set(key, { count: 1, resetTime });
    return {
      allowed: true,
      limit,
      remaining: limit - 1,
      resetTime
    };
  }

  // Under window, increment count
  info.count += 1;
  cache.set(key, info);

  const allowed = info.count <= limit;
  return {
    allowed,
    limit,
    remaining: Math.max(0, limit - info.count),
    resetTime: info.resetTime
  };
}
