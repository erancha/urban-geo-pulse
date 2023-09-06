package com.urbangeopulse.sorter;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.logging.Logger;

/**
 * Basic, non-threads-safe, redis-based remote cache.
 * TODO: Handle Garbage Collection!
 *      Docker Desktop:
 *      1. development-redis-server-1
 *      2. Open in terminal
 *      3. /data # redis-cli
 *      4. keys *
 *      5. flushall
 */
public class RemoteCache implements Cache {
    private static final Logger logger = Logger.getLogger(RemoteCache.class.getName());

    private static final JedisPool jedisPool = createJedisPool();
    // max time to retry put, get and remove requests.

    private static JedisPool createJedisPool() {
        return new JedisPool(buildPoolConfig(), getRedisHostName(), getRedisPort(), getRedisRequestTimeoutInMS());
    }

    private static String getRedisHostName() {
        final String REDIS_HOST_NAME = System.getenv("REDIS_HOST_NAME");
        return REDIS_HOST_NAME != null ? REDIS_HOST_NAME : "localhost";
    }
    private static int getRedisPort() {
        final String REDIS_PORT = System.getenv("REDIS_PORT");
        return REDIS_PORT != null ? Integer.parseInt(REDIS_PORT) : 6379;
    }

    private static int getRedisRequestTimeoutInMS() {
        final String REDIS_REQUEST_TIMEOUT_IN_SEC = System.getenv("REDIS_REQUEST_TIMEOUT_IN_SEC");
        return (REDIS_REQUEST_TIMEOUT_IN_SEC != null ? Integer.parseInt(REDIS_REQUEST_TIMEOUT_IN_SEC) : 10) * 1000;
    }

    private static JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(80);
        poolConfig.setMaxIdle(60);
        poolConfig.setMinIdle(10);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);
        poolConfig.setNumTestsPerEvictionRun(0);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }

    private Jedis getJedisResource() {
        return jedisPool.getResource();
    }

    private void returnJedisResource(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }

    /**
     * @param key
     * @param value
     * @return
     */
    public String put(String key, String value) {
        Jedis jedis = getJedisResource();
        String ret = jedis.set(key, value);
        returnJedisResource(jedis);
        return ret;
    }

    /**
     * @param key
     * @return
     */
    @Override
    public String get(String key) {
        Jedis jedis = getJedisResource();
        String value = jedis.get(key);
        returnJedisResource(jedis);
        return value;
    }

    /**
     * @param key
     */
    @Override
    public void remove(String key) {
        Jedis jedis = getJedisResource();
        jedis.del(key);
        returnJedisResource(jedis);
    }
}