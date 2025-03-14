package main.shared.messages;

public final class MessageType {

    private MessageType() {
    }

    // Client -> Server
    public static final String AUTH_REQUEST = "AUTH_REQUEST";
    public static final String START_REQUEST = "START_REQUEST";
    public static final String LOGOUT_REQUEST = "LOGOUT_REQUEST";
    public static final String DATA_REQUEST = "DATA_REQUEST";

    // Server -> Client
    public static final String START_RESPONSE = "START_RESPONSE";
    public static final String AUTH_RESPONSE = "AUTH_RESPONSE";
    public static final String LOGOUT_RESPONSE = "LOGOUT_RESPONSE";
    public static final String DATA_RESPONSE = "DATA_RESPONSE";

    public static final String SERVER_INFO = "SERVER_INFO";
    public static final String DISCONNECT = "DISCONNECT";
    public static final String ERROR = "ERROR";

    public static String[] getAllTypes() {
        return new String[] { AUTH_REQUEST, START_REQUEST, LOGOUT_REQUEST, DATA_REQUEST,
                START_RESPONSE, AUTH_RESPONSE, LOGOUT_RESPONSE, DATA_RESPONSE };
    }
}
