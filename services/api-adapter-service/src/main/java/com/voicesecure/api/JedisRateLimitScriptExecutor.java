package com.voicesecure.api;

import java.net.URI;
import java.util.List;
import redis.clients.jedis.JedisPooled;

public final class JedisRateLimitScriptExecutor implements RedisApiRateLimiter.ScriptExecutor, AutoCloseable {
    private final JedisPooled jedis;

    public JedisRateLimitScriptExecutor(URI redisUri) {
        if (redisUri == null || !("redis".equals(redisUri.getScheme()) || "rediss".equals(redisUri.getScheme()))) {
            throw new IllegalArgumentException("REDIS_URI must use redis:// or rediss://");
        }
        this.jedis = new JedisPooled(redisUri);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> execute(String script, String key, int capacity, long windowMillis) {
        return (List<Long>) (List<?>) jedis.eval(script, List.of(key), List.of(Integer.toString(capacity), Long.toString(windowMillis)));
    }

    @Override public void close() { jedis.close(); }
}
