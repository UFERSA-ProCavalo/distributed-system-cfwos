package main.client;

import main.shared.log.Logger;

public class ServiceError {
    private final Logger logger = null;

    public ServiceError(Logger logger) {
        //this.logger = logger;
    }

    public void handleError(String source, String message) {
        System.out.println("[ERROR]: " + message);
        logger.error(source + ": " + message);
    }

    public void handleException(String operation, Exception e) {
        logger.error("Error during " + operation, e);
    }
}
