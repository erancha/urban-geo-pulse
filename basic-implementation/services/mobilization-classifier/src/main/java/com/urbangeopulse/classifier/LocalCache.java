package com.urbangeopulse.classifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The simplest, non-threads-safe local cache.
 */
public class LocalCache implements Cache {
    private final Map<String, String> map = new HashMap<>();

    @Override
    public String put(String key, String value) {
        return map.put(key, value);
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }

    @Override
    public void remove(String key) { map.remove(key); }
}
