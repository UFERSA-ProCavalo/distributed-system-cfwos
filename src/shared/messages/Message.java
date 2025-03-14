package shared.messages;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Message implements Serializable {
    // TODO Ler a documentação do Serializable e o que é serialVersionUID
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String type;
    private final Instant timestamp;
    private final String sender;
    private final String recipient;
    private final Object payload;

    public Message(String type, String sender, String recipient, Object payload) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.timestamp = Instant.now();
        this.sender = sender;
        this.recipient = recipient;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        String message = String.format(
                "Message[id=%s, type='%s', timestamp='%s', from='%s', to='%s']", id, type,
                timestamp, sender, recipient);

        return message;
    }
}
