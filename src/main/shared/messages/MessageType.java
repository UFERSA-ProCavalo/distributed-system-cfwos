package main.shared.messages;

/**
 * Defines all possible message types in the system.
 * Using enum ensures type safety and prevents inconsistent usage.
 */
public enum MessageType {
    // Client -> Server
    AUTH_REQUEST,
    START_REQUEST,
    LOGOUT_REQUEST,
    DATA_REQUEST,
    RECONNECT,

    // Server -> Client
    AUTH_RESPONSE,
    LOGOUT_RESPONSE,
    DATA_RESPONSE,

    // Server -> Server
    PROXY_REGISTRATION_REQUEST,
    PROXY_REGISTRATION_RESPONSE,
    // HEARTBEAT_REQUEST,
    // HEARTBEAT_RESPONSE,
    PING,
    PONG,

    // Shared
    SERVER_INFO,
    DISCONNECT,
    ERROR,

    PROXY_PEER_INFO;

    /**
     * Get all message types
     */
    public static MessageType[] getAllTypes() {
        return MessageType.values();
    }
}
