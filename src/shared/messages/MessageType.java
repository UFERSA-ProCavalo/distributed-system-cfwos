package shared.messages;

public final class MessageType {
    // auth
    public static final String AUTH_REQUEST = "AUTH_REQUEST";
    public static final String AUTH_RESPONSE = "AUTH_RESPONSE";

    // socket connection
    public static final String SERVICE_LOOKUP = "SERVICE_LOOKUP";
    public static final String SERVICE_RESPONSE = "SERVICE_RESPONSE";
    public static final String SERVICE_REGISTER = "SERVICE_REGISTER";

    // Aplicação
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String STATUS_UPDATE = "STATUS_UPDATE";
    public static final String DATA_REQUEST = "DATA_REQUEST";
    public static final String DATA_RESPONSE = "DATA_RESPONSE";

    // Sistema
    public static final String HEARTBEAT = "SYSTEM_HEARTBEAT";
    public static final String ERROR = "SYSTEM_ERROR";
    public static final String SERVER_INFO = "SERVER_INFO";
    public static final String DISCONNECT = "SYSTEM_DISCONNECT";

    private MessageType() {
    }
}
