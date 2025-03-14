package main.shared.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapUtil {

    private MapUtil() {
        throw new IllegalStateException("Utility class");
    }
    
    public static <K, V> Map<K, V> of(K key1, V value1, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide key-value pairs.");
        }

        Map<K, V> map = new HashMap<>();
        map.put(key1, value1);

        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) keyValues[i];
            @SuppressWarnings("unchecked")
            V value = (V) keyValues[i + 1];
            map.put(key, value);
        }

        return Collections.unmodifiableMap(map);
    }
}
