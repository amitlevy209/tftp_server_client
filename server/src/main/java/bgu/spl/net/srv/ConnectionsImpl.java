package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private Map<Integer, ConnectionHandler<T>> handlers = new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        handlers.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> ch = handlers.get(connectionId);
        if (ch == null){
            return false;
        }
        ch.send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> ch = handlers.get(connectionId);
        handlers.remove(connectionId);

        try {
            ch.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}