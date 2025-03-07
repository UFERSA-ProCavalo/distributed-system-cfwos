package server.proxy_server;

import server.model.User;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationService {
    private static final Map<String, User> users = new HashMap<>();

    static {
        // Add sample users
        users.put("admin", new User("admin", "admin123"));
        users.put("teste", new User("teste", "teste"));
    }

    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.checkPassword(password);
    }
}
