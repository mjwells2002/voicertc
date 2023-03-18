package xyz.breadloaf.voicertc.sockets.interfaces;

import java.net.SocketAddress;

public interface MultiplexedSocketParentInterface {
    void addClient(MultiplexedSocketClientInterface clientInterface, boolean canOpen);
    void sendPacket(byte[] packet, SocketAddress target) throws Exception;
    boolean open(MultiplexedSocketClientInterface client, short port, String bind) throws Exception;
    boolean isOpen();
    short getLocalPort();
    void close(MultiplexedSocketClientInterface client);
}
