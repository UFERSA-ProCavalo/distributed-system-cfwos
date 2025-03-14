package main.shared.utils;

public class StringUtil {

    private StringUtil() {
        throw new IllegalStateException("Utility class");
    }
    public static String repeat(String str, int count) {
        if (str == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // Test
    public static void main(String[] args) {
        System.out.println(repeat("Hello ", 3)); // Output: "Hello Hello Hello "
        System.out.println(repeat("A", 5)); // Output: "AAAAA"
        System.out.println(repeat("Java", 0)); // Output: ""
    }
}
