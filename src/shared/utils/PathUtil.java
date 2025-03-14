package shared.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {

    /*
     * Classe utilitária para criar instâncias de Path de forma mais simples.
     * Implementação feita para substituir o uso do método Paths.get() que é mais
     * verboso e também simular o Path.of() que só está disponível a partir do Java
     * 11.
     * 
     * https://medium.com/%40AlexanderObregon/javas-paths-get-method-explained-9586c13f2c5c
     */

    private PathUtil() {
        throw new IllegalStateException("Utility class");
    }
    
    public static Path of(String first, String... more) {
        return Paths.get(first, more);
    }

    public static void main(String[] args) {
        Path logFilePath = PathUtil.of("logs", "2024-03-13", "app_12345.log");
        System.out.println(logFilePath); // Output: logs/2024-03-13/app_12345.log
    }
}
