package xyz.breadloaf.voicertc.packets;

import de.maxhenkel.voicechat.api.RawUdpPacket;

import java.net.DatagramPacket;
import java.net.SocketAddress;

public class UDPPacket implements RawUdpPacket {
    public SocketAddress socketAddress;
    public long timestamp = 0;
    public byte[] bytes = null;
    public UDPPacket() {}
    public UDPPacket(DatagramPacket packet, long timestamp) {
        socketAddress = packet.getSocketAddress();
        this.timestamp = timestamp;
        bytes = new byte[packet.getLength()];
        System.arraycopy(packet.getData(),packet.getOffset(),bytes,0,packet.getLength());
    }
    @Override
    public byte[] getData() {
        return bytes;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

}
