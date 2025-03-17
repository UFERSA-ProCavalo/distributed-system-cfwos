package main.client;

public class ServiceInput {

}

interface ClientCommand {
    void execute();
}

class AddWorkOrderCommand implements ClientCommand {
    private final ImplClient client;

    public AddWorkOrderCommand(ImplClient client) {
        this.client = client;
    }

    public void execute() {
        // Add work order implementation
    }
}
