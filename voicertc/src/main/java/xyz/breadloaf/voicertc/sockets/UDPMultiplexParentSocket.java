package xyz.breadloaf.voicertc.sockets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.breadloaf.voicertc.packets.UDPPacket;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketClientInterface;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;

public class UDPMultiplexParentSocket extends Thread implements MultiplexedSocketParentInterface  {
    Logger SocketLogger = LogManager.getLogger("VoiceRTCSocket");
    HashMap<MultiplexedSocketClientInterface, Boolean> clients = new HashMap<>(); //key == client, value == can open socket
    public DatagramSocket socket;

    public UDPMultiplexParentSocket() {
        setName("VoiceRTC Socket Thread");
        setDaemon(true);
    }

    @Override
    public void addClient(MultiplexedSocketClientInterface clientInterface, boolean canOpen) {
        clientInterface.setParent(this);
        clients.put(clientInterface,canOpen);
    }

    @Override
    public void sendPacket(byte[] packet, SocketAddress target) throws Exception {
        socket.send(new DatagramPacket(packet, packet.length, target));

    }

    @Override
    public boolean open(MultiplexedSocketClientInterface client, short port, String bind) throws Exception {
        if (!clients.getOrDefault( client,false)) {
            return socket == null;
        }
        InetAddress bindAddress = null;
        if (socket == null) {
            try {
                if(!bind.isEmpty()) {
                    bindAddress = InetAddress.getByName(bind);
                }
            } catch (UnknownHostException e) {
                SocketLogger.warn("Failed to parse bind '{}'", bind);
            }

            try {
                try {
                    socket = new DatagramSocket(port,bindAddress);
                } catch (SocketException e) {
                    SocketLogger.warn("Failed to bind to '{}' trying wildcard ip", bind);
                    socket = new DatagramSocket(port);
                }
                socket.setTrafficClass(0x04); //0x04 is IPTOS_RELIABILITY but maybe this should be 0x10 (LOW_LATENCY)
            } catch (SocketException e){
                SocketLogger.warn("Failed to bind port '{}' another application may be using this port", port);
                throw e;
            }

        }
        if (socket != null) {
            start();
        }
        return socket == null;
    }

    @Override
    public boolean isOpen() {
        return socket != null;
    }

    @Override
    public short getLocalPort() {
        return (short) socket.getLocalPort();
    }

    @Override
    public void close(MultiplexedSocketClientInterface client) {
        if (clients.getOrDefault(client,false)) {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } else {
            clients.remove(client);
        }

    }

    public void onPacket(UDPPacket packet) {
        for(MultiplexedSocketClientInterface client : clients.keySet()) {
            if (client.matchPacket(packet.bytes)) {
                client.onPacket(packet);
            }
        }
    }

    @Override
    public void run() {
        for(;!socket.isClosed();) //fuck you burger
            try {
                DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
                socket.receive(packet);
                onPacket(new UDPPacket(packet, System.currentTimeMillis()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }
}
