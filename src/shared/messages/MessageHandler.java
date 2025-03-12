package shared.messages;

public interface MessageHandler {
    Message handleMessage(Message message);

    boolean isValidMessage(String messageType);

    String[] getHandledTypes();
}
