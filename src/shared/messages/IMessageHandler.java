package shared.messages;

public interface IMessageHandler {
    Message handleMessage(Message message);

    boolean isValidMessage(String messageType);

    String[] getHandledTypes();
}
