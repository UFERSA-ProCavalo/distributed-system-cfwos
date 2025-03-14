package main.shared.utils;

import java.util.Map;
import java.util.Optional;

/**
 * Classe de utilitários para realizar operações de pattern matching.
 * Disponível apenas a partir do java 16.
 * Detesto usar java 8, mas como é um projeto de estudo, vou usar.
 * A recomendação é usar Java 21 e colocar um hotspot na lib do projeto. 👌
 * referências:
 * https://www.baeldung.com/java-pattern-matching-instanceof#java-16
 * https://stackoverflow.com/questions/262367/type-safety-unchecked-cast
 * 
 * 
 * "The problem is that a cast is a runtime check - but due to type erasure, at
 * runtime there's actually no difference between a HashMap<String,String> and
 * HashMap<Foo,Bar> for any other Foo and Bar."
 * 
 * Um @SuppressWarnings("unchecked") resolve o problema, mas não é uma boa
 * prática.
 */
public class TypeUtil {
    private TypeUtil() {
        throw new IllegalStateException("Utility class");
    }
    public static <K, V> Optional<Map<K, V>> safeCastToMap(Object obj, Class<K> keyType, Class<V> valueType) {
        if (obj instanceof Map) {
            Map<?, ?> tempMap = (Map<?, ?>) obj;

            boolean valid = tempMap.keySet().stream().allMatch(keyType::isInstance) &&
                    tempMap.values().stream().allMatch(valueType::isInstance);

            if (valid) {
                @SuppressWarnings("unchecked")
                Map<K, V> safeMap = (Map<K, V>) tempMap;
                return Optional.of(safeMap);
            }
        }
        return Optional.empty();
    }
}