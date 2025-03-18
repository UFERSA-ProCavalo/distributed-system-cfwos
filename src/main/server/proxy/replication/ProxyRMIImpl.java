package main.server.proxy.replication;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import main.server.proxy.cache.CacheFIFO;
import main.shared.log.Logger;
import main.shared.models.WorkOrder;

/**
 * Implementation of ProxyRMI interface
 */
public class ProxyRMIImpl extends UnicastRemoteObject implements ProxyRMI {
    private final String proxyId;
    private final CacheFIFO<WorkOrder> cache;
    private final Logger logger;

    public ProxyRMIImpl(String proxyId, CacheFIFO<WorkOrder> cache, Logger logger) throws RemoteException {
        super();
        this.proxyId = proxyId;
        this.cache = cache;
        this.logger = logger;
    }

    @Override
    public WorkOrder searchCache(int code) throws RemoteException {
        logger.info("RMI: Remote proxy searching for work order with code {}", code);
        
        try {
            // Print cache details for debugging
            logger.info("Current cache size: {}", cache.getSize());
            
            // Create a search criteria WorkOrder with just the code
            WorkOrder searchCriteria = new WorkOrder(code, null, null);
            
            // Search the cache using our criteria
            WorkOrder result = cache.searchByCode(searchCriteria);
            
            if (result != null) {
                logger.info("RMI: Found work order with code {} in cache: {}", code, result);
                logger.info("Result fields: code={}, name={}, description={}", 
                          result.getCode(), result.getName(), result.getDescription());
            } else {
                logger.info("RMI: Work order with code {} not found in cache", code);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("RMI: Error searching cache: {}", e.getMessage(), e);
            throw new RemoteException("Error searching cache", e);
        }
    }

    @Override
    public String getProxyId() throws RemoteException {
        return proxyId;
    }
}