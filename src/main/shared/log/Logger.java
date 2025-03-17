package main.shared.log;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import main.shared.utils.StringUtil;

/**
 * Classe de utilidade para registro de logs do sistema.
 * 
 * Esta classe implementa um sistema de logging completo com as seguintes
 * funcionalidades:
 * 
 * Registros em múltiplos níveis (DEBUG, INFO, WARNING, ERROR)
 * Saída simultânea para console e arquivos
 * Formatação de mensagens com cores no console
 * Organização de logs em diretórios por data
 * Registro de timestamp, nome do componente e thread
 * Substituição de parâmetros em mensagens estilo slf4j ({})
 * Singleton por nome de componente para evitar múltiplas instâncias
 * 
 */
public class Logger {
    /**
     * Armazena instâncias de loggers por chave (componentName@logDir).
     * Usado para implementar o padrão singleton por componente.
     */
    private static final Map<String, Logger> loggerInstances = new HashMap<>();
    // Formato de timestamp para mensagens de log
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    // Formato de data e hora para criação de diretórios de log
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // Formato de hora para criação de arquivos de log
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private final String componentName;
    private final Path logFilePath;
    private PrintWriter fileWriter;
    private LogType consoleLogLevel = LogType.INFO;
    private LogType fileLogLevel = LogType.DEBUG;
    private Boolean hideConsoleOutput = false;

    /**
     * Construtor privado para implementação do padrão singleton.
     * Inicializa o logger com um nome de componente e diretório específicos.
     *
     * @param componentName  Nome do componente que está utilizando o logger
     * @param logDir         Diretório base onde os logs serão armazenados
     * @param customFileName Nome de arquivo personalizado (opcional, pode ser null)
     */
    private Logger(String componentName, Path logDir, String customFileName) {
        this.componentName = componentName;

        // Cria estrutura de diretório diário
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        String timeStamp = LocalDateTime.now().format(TIME_FORMATTER);

        // Caminho completo: [package_path]/logs/[date]/[component]_[time].log
        Path logsDir = logDir.resolve("logs");
        Path dailyDir = logsDir.resolve(today);
        Path debugDir = logsDir.resolve("_debug.log");
        dailyDir.toFile().mkdirs();

        // Define o caminho do arquivo de log - usa nome personalizado se fornecido
        if (customFileName != null && !customFileName.isEmpty()) {
            this.logFilePath = dailyDir.resolve(customFileName + ".log");
        } else {
            this.logFilePath = dailyDir.resolve(componentName + "_" + timeStamp + ".log");
        }

        try {
            debugDir.toFile().createNewFile();
        } catch (Exception e) {
            System.err.println("Failed to create debug log file: " + e.getMessage());
        }

        // Inicializa o arquivo de log
        initializeLogFile();
    }

    /**
     * Construtor privado original para compatibilidade com código existente
     */
    private Logger(String componentName, Path logDir) {
        this(componentName, logDir, null);
    }

    /**
     * Inicializa o arquivo de log com cabeçalho.
     * Cria o arquivo, adiciona separadores e timestamp de início.
     */
    private void initializeLogFile() {
        try {
            fileWriter = new PrintWriter(new FileWriter(logFilePath.toFile()));
            fileWriter.println(StringUtil.repeat("=", 80));
            fileWriter.println("Log started at " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            fileWriter.println(StringUtil.repeat("=", 80));
            fileWriter.flush();

            System.out.println("Log file: " + logFilePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create log file: " + e.getMessage());
        }
    }

    /**
     * Obtém um logger que automaticamente determina o nome do componente e
     * caminho baseado na classe chamadora.
     *
     * @return Uma instância de Logger configurada para a classe chamadora
     */
    public static Logger getLogger() {
        // Encontra a classe chamadora
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();

        try {
            // Obtém a classe
            Class<?> callerClass = Class.forName(callerClassName);
            String componentName = callerClass.getSimpleName();

            // Obtém o caminho do diretório do pacote
            Path packagePath = getPackagePath(callerClass);

            return getLogger(componentName, packagePath);
        } catch (ClassNotFoundException e) {
            // Fallback para o diretório atual
            return getLogger("Unknown", Paths.get(""));
        }
    }

    /**
     * Obtém um logger com nome de arquivo personalizado baseado na classe
     * chamadora.
     *
     * @param customFileName Nome de arquivo personalizado para o log (sem extensão)
     * @return Uma instância de Logger configurada para a classe chamadora
     */
    public static Logger getLogger(String customFileName) {
        // Encontra a classe chamadora
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();

        try {
            // Obtém a classe
            Class<?> callerClass = Class.forName(callerClassName);
            String componentName = callerClass.getSimpleName();

            // Obtém o caminho do diretório do pacote
            Path packagePath = getPackagePath(callerClass);

            return getLogger(componentName, packagePath, customFileName);
        } catch (ClassNotFoundException e) {
            // Fallback para o diretório atual
            return getLogger("Unknown", Paths.get(""), customFileName);
        }
    }

    /**
     * Obtém o caminho do sistema de arquivos correspondente ao pacote da classe
     * fornecida.
     *
     * @param clazz Classe para a qual o caminho do pacote será determinado
     * @return O caminho (Path) do diretório do pacote
     */
    private static Path getPackagePath(Class<?> clazz) {
        try {
            // Converte o nome do pacote para caminho
            String packageName = clazz.getPackage().getName();
            String packagePath = packageName.replace('.', '/');

            // Obtém o caminho do arquivo de classe
            URI uri = clazz.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path basePath;

            String uriPath = uri.getPath();
            if (uriPath.endsWith(".jar")) {
                // Se executando de um JAR, usa o diretório pai
                basePath = Paths.get(uri).getParent();
            } else {
                // Se executando a partir de classes, encontra o diretório base
                basePath = Paths.get(uri);

                // Navega para o diretório src se estamos em um diretório bin/classes
                if (basePath.endsWith("bin") || basePath.endsWith("classes")) {
                    basePath = basePath.getParent().resolve("src");
                }
            }

            // Combina o caminho base com o caminho do pacote
            return basePath.resolve(packagePath);

        } catch (URISyntaxException e) {
            // Fallback para o diretório atual
            System.err.println("Error determining package path: " + e.getMessage());
            return Paths.get("");
        }
    }

    /**
     * Obtém um logger com nome de componente e diretório de log explícitos.
     *
     * @param componentName Nome do componente que está usando o logger
     * @param logDir        Diretório onde os logs serão armazenados
     * @return Uma instância de Logger configurada com os parâmetros fornecidos
     */
    public static Logger getLogger(String componentName, Path logDir) {
        return getLogger(componentName, logDir, null);
    }

    /**
     * Obtém um logger com nome de componente, diretório e nome de arquivo
     * personalizados.
     *
     * @param componentName  Nome do componente que está usando o logger
     * @param logDir         Diretório onde os logs serão armazenados
     * @param customFileName Nome de arquivo personalizado para o log (sem extensão)
     * @return Uma instância de Logger configurada com os parâmetros fornecidos
     */
    public static Logger getLogger(String componentName, Path logDir, String customFileName) {
        String key = componentName + "@" + logDir + (customFileName != null ? "#" + customFileName : "");
        synchronized (loggerInstances) {
            if (!loggerInstances.containsKey(key)) {
                loggerInstances.put(key, new Logger(componentName, logDir, customFileName));
            }
            return loggerInstances.get(key);
        }
    }

    /**
     * Define se a saída do console deve ser ocultada.
     *
     * @param hideConsoleOutput true para ocultar a saída do console, false para
     *                          exibir
     */
    public void setHideConsoleOutput(boolean hide) {
        this.hideConsoleOutput = hide;
    }

    public void setHideConsoleOutputAll(boolean hide) {
        for (Logger logger : loggerInstances.values()) {
            logger.setHideConsoleOutput(hide);
        }
    }

    /**
     * Registra uma mensagem com nível DEBUG.
     *
     * @param message A mensagem a ser registrada
     */
    public void debug(String message) {
        log(LogType.DEBUG, message);
    }

    /**
     * Registra uma mensagem com nível INFO.
     *
     * @param message A mensagem a ser registrada
     */
    public void info(String message) {
        log(LogType.INFO, message);
    }

    /**
     * Registra uma mensagem com nível WARNING.
     *
     * @param message A mensagem a ser registrada
     */
    public void warning(String message) {
        log(LogType.WARNING, message);
    }

    /**
     * Registra uma mensagem com nível ERROR.
     *
     * @param message A mensagem a ser registrada
     */
    public void error(String message) {
        log(LogType.ERROR, message);
    }

    /**
     * Insere uma linha em branco no log para melhorar a legibilidade.
     */
    public void br() {
        log(LogType.INFO, "");
    }

    /**
     * Obtém a cor ANSI para o nível de log especificado.
     *
     * @param level O nível de log
     * @return A string de código de cor ANSI correspondente
     */
    private String getColorForLevel(LogType level) {
        switch (level) {
            case DEBUG:
                return ConsoleColors.CYAN;
            case INFO:
                return ConsoleColors.GREEN;
            case WARNING:
                return ConsoleColors.YELLOW;
            case ERROR:
                return ConsoleColors.RED;
            default:
                return ConsoleColors.RESET;
        }
    }

    /**
     * Método principal de log que processa e registra a mensagem nos destinos
     * apropriados.
     *
     * @param level   O nível do log (DEBUG, INFO, WARNING, ERROR)
     * @param message A mensagem a ser registrada
     */
    private void log(LogType level, String message) {
        // Ignora se abaixo dos limites de nível

        synchronized (Logger.class) {
            if (level.getLevel() < consoleLogLevel.getLevel() &&
                    level.getLevel() < fileLogLevel.getLevel()) {
                return;
            }

            // Formata a mensagem
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String threadName = Thread.currentThread().getName();

            // Cria versão colorida para o console
            String coloredMessage = String.format("%s[%s]%s %s[%s]%s %s[%s]%s [%s] %s",
                    ConsoleColors.BLUE, timestamp, ConsoleColors.RESET,
                    ConsoleColors.WHITE_BOLD, componentName, ConsoleColors.RESET,
                    getColorForLevel(level), level.getLabel(), ConsoleColors.RESET,
                    threadName, message);

            // Cria versão simples para arquivo
            String plainMessage = String.format("[%s] [%s] [%s] [%s] %s",
                    timestamp, componentName, level.getLabel(), threadName, message);

            // Saída para console
            if (!hideConsoleOutput && level.getLevel() >= consoleLogLevel.getLevel()) {
                if (level == LogType.ERROR) {
                    System.err.println(coloredMessage);
                } else {
                    System.out.println(coloredMessage);
                }
            }

            // Saída para arquivo (texto simples sem cores)
            if (fileWriter != null && level.getLevel() >= fileLogLevel.getLevel()) {
                synchronized (this) {
                    fileWriter.println(plainMessage);
                    fileWriter.flush();
                }
            }
        }
    }

    /**
     * Registra explicitamente uma mensagem tanto no console quanto no arquivo de
     * log,
     * ignorando qualquer lógica de redirecionamento.
     *
     * @param level   O nível do log
     * @param message A mensagem a ser registrada
     */
    public void logToFile(LogType level, String message) {
        // Formata a mensagem
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String threadName = Thread.currentThread().getName();

        // Cria versão colorida para o console
        String coloredMessage = String.format("%s[%s]%s %s[%s]%s %s[%s]%s [%s] %s",
                ConsoleColors.BLUE, timestamp, ConsoleColors.RESET,
                ConsoleColors.WHITE_BOLD, componentName, ConsoleColors.RESET,
                getColorForLevel(level), level.getLabel(), ConsoleColors.RESET,
                threadName, message);

        // Cria versão simples para arquivo
        String plainMessage = String.format("[%s] [%s] [%s] [%s] %s",
                timestamp, componentName, level.getLabel(), threadName, message);

        synchronized (this) {
            // Escreve versão colorida no console
            if (level == LogType.ERROR) {
                originalErr.println(coloredMessage);
            } else {
                originalOut.println(coloredMessage);
            }

            // Escreve versão simples no arquivo
            if (fileWriter != null && level.getLevel() >= fileLogLevel.getLevel()) {
                fileWriter.println(plainMessage);
                fileWriter.flush();
            }
        }
    }

    /**
     * Fecha o logger, finalizando com um rodapé apropriado e liberando recursos.
     */
    public void close() {
        synchronized (this) {
            if (fileWriter != null) {
                fileWriter.println(StringUtil.repeat("=", 80));
                fileWriter.println("Log closed at " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
                fileWriter.println(StringUtil.repeat("=", 80));
                fileWriter.close();
                fileWriter = null;
            }
        }
    }

    /**
     * Define o nível mínimo de log para exibição no console.
     *
     * @param level O nível mínimo de log que será exibido no console
     */
    public void setConsoleLogLevel(LogType level) {
        this.consoleLogLevel = level;
    }

    /**
     * Define o nível mínimo de log para gravação no arquivo.
     *
     * @param level O nível mínimo de log que será gravado no arquivo
     */
    public void setFileLogLevel(LogType level) {
        this.fileLogLevel = level;
    }

    /**
     * Stream de saída para depuração que não será recursivamente logado.
     */
    private static PrintStream debugStream;

    /**
     * Obtém um PrintStream que pode ser usado para saída de depuração
     * que não será recursivamente registrada no log.
     *
     * @return Um PrintStream para saída de depuração
     */
    public static synchronized PrintStream getDebugStream() {
        if (debugStream == null) {
            debugStream = new PrintStream(System.out) {
                @Override
                public void println(String x) {
                    super.println("[DEBUG] " + x);
                }
            };
        }
        return debugStream;
    }

    /**
     * Formata uma mensagem substituindo placeholders {} por argumentos.
     * Similar ao estilo de formatação do SLF4J.
     *
     * @param message A mensagem com placeholders
     * @param args    Os argumentos para substituir os placeholders
     * @return A mensagem formatada com os argumentos inseridos
     */
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        boolean escapeNext = false;

        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);

            if (escapeNext) {
                result.append(current);
                escapeNext = false;
                continue;
            }

            if (current == '\\') {
                escapeNext = true;
                continue;
            }

            if (current == '{' && i < message.length() - 1 && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    result.append(args[argIndex++]);
                    i++; // Pula o fechamento de chave
                } else {
                    result.append("{}");
                    i++;
                }
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    /**
     * Registra uma mensagem de nível DEBUG com substituição de argumentos.
     *
     * @param message A mensagem com placeholders {}
     * @param args    Os argumentos para substituir os placeholders
     */
    public void debug(String message, Object... args) {
        log(LogType.DEBUG, formatMessage(message, args));
    }

    /**
     * Registra uma mensagem de nível INFO com substituição de argumentos.
     *
     * @param message A mensagem com placeholders {}
     * @param args    Os argumentos para substituir os placeholders
     */
    public void info(String message, Object... args) {
        log(LogType.INFO, formatMessage(message, args));
    }

    /**
     * Registra uma mensagem de nível WARNING com substituição de argumentos.
     *
     * @param message A mensagem com placeholders {}
     * @param args    Os argumentos para substituir os placeholders
     */
    public void warning(String message, Object... args) {
        log(LogType.WARNING, formatMessage(message, args));
    }

    /**
     * Registra uma mensagem de nível ERROR com substituição de argumentos.
     *
     * @param message A mensagem com placeholders {}
     * @param args    Os argumentos para substituir os placeholders
     */
    public void error(String message, Object... args) {
        log(LogType.ERROR, formatMessage(message, args));
    }

    /**
     * Registra uma exceção com sua stack trace no nível ERROR.
     *
     * @param message   A mensagem descritiva do erro
     * @param throwable A exceção a ser registrada
     */
    public void error(String message, Throwable throwable) {
        error(message + ": {}", throwable.getMessage());
        for (StackTraceElement element : throwable.getStackTrace()) {
            error("\tat {}", element);
        }
    }
}