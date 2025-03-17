package main.client;

public class ServiceAuth {
    private int loginTries = 0;
    private String username;
    private String password;
    private final int MAX_LOGIN_ATTEMPTS = 3;

    // Authentication methods
    public boolean authenticate(String username, String password) {
        return false;
    }

    public boolean hasExceededMaxAttempts() {
        return loginTries >= MAX_LOGIN_ATTEMPTS;
    }
}
