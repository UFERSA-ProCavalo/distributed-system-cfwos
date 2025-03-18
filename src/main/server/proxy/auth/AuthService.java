package main.server.proxy.auth;

import java.util.HashMap;
import java.util.Map;

import main.shared.models.User;

public class AuthService {
    private static final Map<String, User> users = new HashMap<>();

    // Singleton instance
    private static AuthService instance;

    private AuthService() {
        // Private constructor to prevent instantiation

    }

    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    static {
        // Add sample users
        users.put("admin", new User("admin", "admin123"));
        users.put("teste", new User("teste", "teste"));
        users.put("sistemas", new User("sistemas", "distribuidos"));
    }

    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.checkPassword(password);
    }
}
