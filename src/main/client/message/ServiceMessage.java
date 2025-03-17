package main.client.message;

import main.client.ImplClient;
import main.shared.messages.Message;

public interface ServiceMessage {
    void handle(Message message, ImplClient client);
}
