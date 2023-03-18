package xyz.breadloaf.voicertc.voicechatapi;

import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;
import xyz.breadloaf.voicertc.VoiceRTC;
import xyz.breadloaf.voicertc.packets.UDPPacket;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketClientInterface;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;

import java.net.SocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VoicechatSocketMultiplexClient implements VoicechatSocket, MultiplexedSocketClientInterface {
    private MultiplexedSocketParentInterface parentSocket = null;

    private LinkedBlockingQueue<UDPPacket> packetReadQueue = new LinkedBlockingQueue<>();
    @Override
    public boolean matchPacket(byte[] packet) {
        //returns true if packet matching is supported and packet[0] is 0xff or packet matching is not supported
        return !VoiceRTC.supportsPacketMatching || packet[0] == (byte) 0xff;
    }

    @Override
    public void setParent(MultiplexedSocketParentInterface parentInterface) {
        this.parentSocket = parentInterface;
    }

    @Override
    public void onPacket(UDPPacket packet) {
        if (isClosed()) {
            return;
        }
        packetReadQueue.add(packet);
    }

    @Override
    public void open(int port, String bindAddress) throws Exception {
        if (parentSocket == null) {
            throw new IllegalStateException("Open on multiplexed socket client with no parent");
        }
        parentSocket.open(this,(short)port,bindAddress);
    }

    @Override
    public RawUdpPacket read() throws Exception {
        RawUdpPacket packet = null;
        while (packet == null) {
            packet = packetReadQueue.poll(1000, TimeUnit.MILLISECONDS);
        }
        return packet;
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        if (parentSocket == null)
            throw new IllegalStateException("Send on parentless socket");
        parentSocket.sendPacket( data, address);

    }

    @Override
    public int getLocalPort() {
        if (parentSocket != null)
            return parentSocket.getLocalPort();
        return 0;
    }

    @Override
    public void close() {
        if (parentSocket != null)
            parentSocket.close(this);
    }

    @Override
    public boolean isClosed() {
        if (parentSocket != null)
            return !parentSocket.isOpen();
        return true;
    }

}
