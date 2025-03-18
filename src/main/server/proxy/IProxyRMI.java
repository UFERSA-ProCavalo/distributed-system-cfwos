package main.server.proxy;

import java.rmi.Remote;
import java.rmi.RemoteException;
import main.shared.models.WorkOrder;

public interface IProxyRMI extends Remote{

    // Checa de se uma ordem de servico existe em uma cache remota
    WorkOrder getWorkOrder(int code) throws RemoteException;

    // Atualiza uma entrada da cache de todos os proxy
    void updateCacheEntry(WorkOrder workOrder) throws RemoteException;

    // Remove uma entrada da cache de todos os proxy
    void removeCacheEntry(int code) throws RemoteException;
}
