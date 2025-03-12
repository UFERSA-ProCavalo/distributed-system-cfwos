package shared.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class LoggingOutputStream extends OutputStream {
    private final OutputStream originalStream;
    private final Consumer<String> logFunction;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);

    // Using a thread-local flag to prevent recursive logging
    private static final ThreadLocal<Boolean> LOGGING_IN_PROGRESS = ThreadLocal.withInitial(() -> false);

    public LoggingOutputStream(OutputStream originalStream, Consumer<String> logFunction) {
        this.originalStream = originalStream;
        this.logFunction = logFunction;
    }

    @Override
    public void write(int b) throws IOException {
        // Write to original stream
        originalStream.write(b);

        // Add to buffer
        buffer.write(b);

        // Process if newline or buffer is getting full
        if (b == '\n' || buffer.size() > 1000) {
            String content = buffer.toString();
            buffer.reset();

            if (!content.trim().isEmpty()) {
                // Process the line without recursive logging
                if (!LOGGING_IN_PROGRESS.get()) {
                    try {
                        LOGGING_IN_PROGRESS.set(true);
                        logFunction.accept(content.trim());
                    } finally {
                        LOGGING_IN_PROGRESS.set(false);
                    }
                }
            }
        }
    }

    @Override
    public void flush() throws IOException {
        originalStream.flush();

        // Process any remaining buffered content
        if (buffer.size() > 0) {
            String line = buffer.toString().trim();
            if (!line.isEmpty() && !LOGGING_IN_PROGRESS.get()) {
                try {
                    LOGGING_IN_PROGRESS.set(true);
                    logFunction.accept(line);
                } finally {
                    LOGGING_IN_PROGRESS.set(false);
                }
            }
            buffer.reset();
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        originalStream.close();
    }
}