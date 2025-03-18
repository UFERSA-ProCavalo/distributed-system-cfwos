package main.server.application.replication;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface HeartBeatService extends Remote{
    boolean isPrimaryAlive() throws RemoteException;
}
