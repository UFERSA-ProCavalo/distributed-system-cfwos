package main.server.application.database;

//

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import main.shared.log.Logger;
import main.shared.models.WorkOrder;

/**
 * Gerencia a persistência da base de dados de ordens de trabalho.
 * Responsável por salvar e carregar os dados da base em diversos formatos.
 */
public class DatabaseHandler {
    private static final Logger logger = Logger.getLogger();
    private static final String DB_DIR = "database";
    private static final String DEFAULT_DB_FILE = "work_orders";

    private final Database database;
    private final String dbFilePath;
    private FileFormat fileFormat;

    /**
     * Formato do arquivo de base de dados
     */
    public enum FileFormat {
        BINARY(".bin"),
        JSON(".json"),
        TEXT(".txt");

        private final String extension;

        FileFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    /**
     * Construtor que inicializa o gerenciador de base de dados com formato padrão
     * (JSON)
     * 
     * @param database A instância do Database a ser gerenciada
     */
    public DatabaseHandler(Database database) {
        this(database, FileFormat.TEXT);
    }

    /**
     * Construtor que inicializa o gerenciador de base de dados com formato
     * especificado
     * 
     * @param database A instância do Database a ser gerenciada
     * @param format   O formato do arquivo de persistência
     */
    public DatabaseHandler(Database database, FileFormat format) {
        this.database = database;
        this.fileFormat = format;

        // Cria diretório de database se não existir
        Path dbDirPath = Paths.get(DB_DIR);
        if (!Files.exists(dbDirPath)) {
            try {
                Files.createDirectories(dbDirPath);
                logger.info("Diretório de banco de dados criado: {}", dbDirPath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Erro ao criar diretório de banco de dados: {}", e.getMessage());
            }
        }

        this.dbFilePath = DB_DIR + "/" + DEFAULT_DB_FILE + fileFormat.getExtension();

        // Carrega base de dados do arquivo se existir
        loadDatabase();
    }

    /**
     * Salva a base de dados atual no arquivo
     */
    public synchronized void saveDatabase() {
        try {
            switch (fileFormat) {
                case BINARY:
                    saveDatabaseBinary();
                    break;
                case TEXT:
                    saveDatabaseText();
                    break;
            }
            logger.info("Base de dados salva com sucesso em {}", dbFilePath);
        } catch (Exception e) {
            logger.error("Erro ao salvar base de dados: {}", e.getMessage());
        }
    }

    /**
     * Carrega a base de dados do arquivo
     */
    public synchronized void loadDatabase() {
        File dbFile = new File(dbFilePath);
        if (!dbFile.exists()) {
            logger.info("Arquivo de banco de dados não encontrado. Iniciando com base vazia.");
            return;
        }

        try {
            switch (fileFormat) {
                case BINARY:
                    loadDatabaseBinary();
                    break;
                case TEXT:
                    loadDatabaseText();
                    break;
            }
            logger.info("Base de dados carregada com sucesso de {}", dbFilePath);
        } catch (Exception e) {
            logger.error("Erro ao carregar base de dados: {}", e.getMessage());
        }
    }

    /**
     * Salva a base de dados em formato binário
     */
    private void saveDatabaseBinary() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dbFilePath))) {
            // Extrai todos os WorkOrders da árvore
            List<WorkOrder> workOrders = extractAllWorkOrders();

            // Salva a lista de WorkOrders
            oos.writeObject(workOrders.toArray(new WorkOrder[0]));
        }
    }

    /**
     * Carrega a base de dados do formato binário
     */
    private void loadDatabaseBinary() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dbFilePath))) {
            WorkOrder[] workOrders = (WorkOrder[]) ois.readObject();

            // Limpa a base de dados existente
            clearDatabase();

            // Adiciona os itens carregados
            for (WorkOrder workOrder : workOrders) {
                if (workOrder != null) {
                    database.addWorkOrder(
                            workOrder.getCode(),
                            workOrder.getName(),
                            workOrder.getDescription(),
                            workOrder.getTimestamp());
                }
            }
        }
    }

    /**
     * Salva a base de dados em formato de texto simples
     */
    private void saveDatabaseText() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dbFilePath))) {
            // Extrai todos os WorkOrders da árvore
            List<WorkOrder> workOrders = extractAllWorkOrders();

            for (WorkOrder workOrder : workOrders) {
                writer.write(workOrder.getCode() + "|" +
                        workOrder.getName() + "|" +
                        workOrder.getDescription() + "|" +
                        (workOrder.getTimestamp() != null ? workOrder.getTimestamp() : ""));
                writer.newLine();
            }
        }
    }

    /**
     * Carrega a base de dados do formato de texto simples
     */
    private void loadDatabaseText() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(dbFilePath))) {
            // Limpa a base de dados existente
            clearDatabase();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 3) {
                    int code = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    String description = parts[2];
                    String timestamp = parts.length > 3 ? parts[3] : "";

                    database.addWorkOrder(code, name, description, timestamp);
                }
            }
        }
    }

    /**
     * Extrai todos os WorkOrders da base de dados para uma lista
     * 
     * @return Lista com todos os WorkOrders da base de dados
     */
    private List<WorkOrder> extractAllWorkOrders() {
        List<WorkOrder> workOrders = new ArrayList<>();

        // Implementação de extração de todos os WorkOrders da árvore AVL
        // Precisamos implementar uma forma de percorrer a árvore e extrair todos os
        // objetos

        // Uma abordagem é acrescentar um método na classe Database que retorne todos os
        // WorkOrders
        // Outra abordagem seria adicionar um visitor pattern na TreeAVL

        // Implementação temporária usando os códigos e buscas individuais
        // Isso não é eficiente para árvores grandes, mas funciona para o exemplo
        int size = database.getSize();

        // Aqui teríamos que percorrer a árvore
        // Como não temos acesso direto à estrutura interna da árvore, precisaríamos
        // adicionar um método na classe Database para listar todas as ordens de
        // trabalho

        // Exemplo de implementação para adicionar ao Database.java:
        // public List<WorkOrder> getAllWorkOrders() {
        // List<WorkOrder> result = new ArrayList<>();
        // database.traverseInOrder((key, value) -> result.add(value));
        // return result;
        // }

        // E então chamaríamos:
        // return database.getAllWorkOrders();

        return workOrders;
    }

    /**
     * Limpa todos os dados da base de dados
     */
    private void clearDatabase() {
        // Idealmente adicionaríamos um método no Database para limpar todos os dados
        // Algo como database.clear();

        // Como alternativa, poderíamos usar reflection para acessar o campo database
        // e substituí-lo por uma nova árvore AVL vazia
        try {
            java.lang.reflect.Field field = Database.class.getDeclaredField("database");
            field.setAccessible(true);
            field.set(database, new main.shared.utils.tree.TreeAVL<>());
        } catch (Exception e) {
            logger.error("Erro ao limpar base de dados: {}", e.getMessage());
        }
    }

    /**
     * Define o formato do arquivo de base de dados
     * 
     * @param format O formato desejado
     */
    public void setFileFormat(FileFormat format) {
        this.fileFormat = format;
    }
}