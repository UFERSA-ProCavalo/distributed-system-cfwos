package main.server.proxy.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import main.shared.models.WorkOrder;
import main.shared.utils.list.LinkedList;
import main.shared.utils.list.LinkedList.Node;

import java.util.HashMap;

/**
 * Implementação de cache utilizando política FIFO (First In, First Out)
 * com sincronização para acesso de múltiplas threads.
 * 
 * @param <V> O tipo de objeto armazenado na cache
 */
public class CacheFIFO<V> {
    private static final int MAX_SIZE = 20;
    private final LinkedList<V> cache;
    private final Object lock = new Object(); // Objeto para sincronização

    /**
     * Cria uma nova instância de cache FIFO vazia
     */
    public CacheFIFO() {
        cache = new LinkedList<>();
    }

    /**
     * Adiciona um item na cache, removendo o item mais antigo se necessário
     * 
     * @param value O valor a ser adicionado
     */
    public void add(V value) {
        if (value == null)
            return;

        synchronized (lock) {
            // Se já existe o item na cache, remova-o primeiro para não haver duplicatas
            V existing = get(value);
            if (existing != null) {
                cache.removeCrit(value);
            }

            // Se atingiu o tamanho máximo, remove o primeiro elemento (mais antigo)
            if (cache.getSize() == MAX_SIZE) {
                cache.removeFirst();
            }

            // Adiciona o novo valor no final da lista
            cache.addLast(value);
        }
    }

    /**
     * Remove um valor específico da cache
     * 
     * @param value O valor a ser removido
     */
    public void remove(V value) {
        synchronized (lock) {
            cache.removeCrit(value);
        }
    }

    /**
     * Busca um valor na cache
     * 
     * @param criteria O critério de busca (ou valor específico)
     * @return O valor encontrado ou null se não encontrado
     */
    public V get(V criteria) {
        synchronized (lock) {
            return cache.search(criteria);
        }
    }

    /**
     * Busca um WorkOrder pelo código
     * 
     * @param code O código do WorkOrder a ser buscado
     * @return O WorkOrder encontrado ou null
     */
    public V searchByCode(V code) {
        synchronized (lock) {
            if (cache.isEmpty()) {
                return null;
            }
            return cache.search(code);
        }
    }

    /**
     * Método auxiliar para busca não sincronizada (para uso interno)
     */
    // private V search(V criteria) {
    // return cache.search(criteria);
    // }

    /**
     * Exibe o conteúdo da cache no console
     */
    public void showCache() {
        synchronized (lock) {
            cache.show();
        }
    }

    /**
     * Retorna o tamanho atual da cache
     * 
     * @return Número de elementos na cache
     */
    public int getSize() {
        synchronized (lock) {
            return cache.getSize();
        }
    }

    /**
     * Verifica se a cache está vazia
     * 
     * @return true se vazia, false caso contrário
     */
    public boolean isEmpty() {
        synchronized (lock) {
            return cache.isEmpty();
        }
    }

    /**
     * Returns a string representation of the current cache contents
     * 
     * @return String containing all cache entries
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public String getCacheContentsAsString() {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n===== CURRENT CACHE (Size: ").append(getSize()).append("/").append(MAX_SIZE)
                    .append(") =====\n");

            if (isEmpty()) {
                sb.append("Cache is empty\n");
            } else {
                // Use the LinkedList's internal structure to get all items
                Node current = cache.getHead();
                int index = 0;

                while (current != null) {
                    V value = (V) current.getData();
                    if (value instanceof WorkOrder) {
                        WorkOrder wo = (WorkOrder) value;
                        sb.append(String.format("[%d] ID: %d | Name: %s | Description: %s\n",
                                index++, wo.getCode(), wo.getName(),
                                wo.getDescription().length() > 20 ? wo.getDescription().substring(0, 20) + "..."
                                        : wo.getDescription()));
                    } else {
                        sb.append(String.format("[%d] %s\n", index++, value));
                    }
                    current = current.getNext();
                }
            }

            sb.append("=====================================\n");
            return sb.toString();
        }
    }

    /**
     * Gets metrics about the cache performance
     */
    public Map<String, Object> getMetrics() {
        synchronized (lock) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("size", cache.getSize());
            metrics.put("maxSize", MAX_SIZE);
            metrics.put("usagePercent", (cache.getSize() * 100.0) / MAX_SIZE);
            return metrics;
        }
    }
}