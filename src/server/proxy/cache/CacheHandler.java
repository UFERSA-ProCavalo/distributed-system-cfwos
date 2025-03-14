package server.proxy.cache;

//TODO Adicionar a possibilidade de salvar como JSON
//TODO Salvar no diretório do pacote

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

import shared.log.Logger;
import shared.models.WorkOrder;

/**
 * Gerencia a persistência da cache do servidor proxy.
 * Responsável por salvar e carregar os dados da cache em diversos formatos.
 */
public class CacheHandler {
    private static final Logger logger = Logger.getLogger();
    private static final String CACHE_DIR = "cache";
    private static final String DEFAULT_CACHE_FILE = "work_order_cache";

    private final CacheFIFO<WorkOrder> cache;
    private final String cacheFilePath;
    private FileFormat fileFormat;

    /**
     * Formato do arquivo de cache
     */
    public enum FileFormat {
        BINARY(".bin"),
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
     * Construtor que inicializa o gerenciador de cache com formato padrão (JSON)
     * 
     * @param cache A instância de CacheFIFO a ser gerenciada
     */
    public CacheHandler(CacheFIFO<WorkOrder> cache) {
        this(cache, FileFormat.TEXT);
    }

    /**
     * Construtor que inicializa o gerenciador de cache com formato especificado
     * 
     * @param cache  A instância de CacheFIFO a ser gerenciada
     * @param format O formato do arquivo de persistência
     */
    public CacheHandler(CacheFIFO<WorkOrder> cache, FileFormat format) {
        this.cache = cache;
        this.fileFormat = format;

        // Cria diretório de cache se não existir
        Path cacheDirPath = Paths.get(CACHE_DIR);
        if (!Files.exists(cacheDirPath)) {
            try {
                Files.createDirectories(cacheDirPath);
                logger.info("Diretório de cache criado: {}", cacheDirPath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Erro ao criar diretório de cache: {}", e.getMessage());
            }
        }

        this.cacheFilePath = CACHE_DIR + "/" + DEFAULT_CACHE_FILE + fileFormat.getExtension();

        // Carrega cache do arquivo se existir
        loadCache();
    }

    /**
     * Salva a cache atual no arquivo
     */
    public synchronized void saveCache() {
        try {
            switch (fileFormat) {
                case BINARY:
                    saveCacheBinary();
                    break;
                case TEXT:
                    saveCacheText();
                    break;
            }
            logger.info("Cache salva com sucesso em {}", cacheFilePath);
        } catch (Exception e) {
            logger.error("Erro ao salvar cache: {}", e.getMessage());
        }
    }

    /**
     * Carrega a cache do arquivo
     */
    public synchronized void loadCache() {
        File cacheFile = new File(cacheFilePath);
        if (!cacheFile.exists()) {
            logger.info("Arquivo de cache não encontrado. Iniciando com cache vazia.");
            return;
        }

        try {
            switch (fileFormat) {
                case BINARY:
                    loadCacheBinary();
                    break;
                case TEXT:
                    loadCacheText();
                    break;
            }
            logger.info("Cache carregada com sucesso de {}", cacheFilePath);
        } catch (Exception e) {
            logger.error("Erro ao carregar cache: {}", e.getMessage());
        }
    }

    /**
     * Salva a cache em formato binário
     */
    private void saveCacheBinary() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFilePath))) {
            // Convertemos para um array porque a implementação interna da LinkedList pode
            // não ser serializable
            WorkOrder[] workOrders = new WorkOrder[cache.getSize()];

            for (int i = 0; i < cache.getSize(); i++) {
                // Precisamos copiar os elementos em ordem
                WorkOrder temp = cache.get(null);
                if (temp != null) {
                    workOrders[i] = temp;
                }
            }

            oos.writeObject(workOrders);
        }
    }

    /**
     * Carrega a cache do formato binário
     */
    private void loadCacheBinary() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFilePath))) {
            WorkOrder[] workOrders = (WorkOrder[]) ois.readObject();

            // Limpa a cache existente
            while (!cache.isEmpty()) {
                cache.remove(null);
            }

            // Adiciona os itens na ordem correta
            for (WorkOrder workOrder : workOrders) {
                if (workOrder != null) {
                    cache.add(workOrder);
                }
            }
        }
    }

    /**
     * Salva a cache em formato de texto simples
     */
    private void saveCacheText() throws IOException {
        logger.info("Salvando cache em formato de texto simples...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFilePath))) {
            for (int i = 0; i < cache.getSize(); i++) {
                WorkOrder workOrder = cache.get(null);
                if (workOrder != null) {
                    writer.write(workOrder.getCode() + "|" +
                            workOrder.getName() + "|" +
                            workOrder.getDescription() + "|" +
                            (workOrder.getTimestamp() != null ? workOrder.getTimestamp() : ""));
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Carrega a cache do formato de texto simples
     */
    private void loadCacheText() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFilePath))) {
            // Limpa a cache existente
            while (!cache.isEmpty()) {
                cache.remove(null);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 3) {
                    int code = Integer.parseInt(parts[0]);
                    String name = parts[1];
                    String description = parts[2];
                    String timestamp = parts.length > 3 ? parts[3] : "";

                    WorkOrder workOrder = new WorkOrder(code, name, description, timestamp);
                    cache.add(workOrder);
                }
            }
        }
    }

    /**
     * Define o formato do arquivo de cache
     * 
     * @param format O formato desejado
     */
    public void setFileFormat(FileFormat format) {
        this.fileFormat = format;
    }
}
