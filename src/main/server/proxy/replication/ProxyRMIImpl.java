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
        return cache.searchByCode(new WorkOrder(code, null, null));
    }

    @Override
    public String getProxyId() throws RemoteException {
        return proxyId;
    }
}