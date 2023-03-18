package xyz.breadloaf.voicertc.sockets.interfaces;

import xyz.breadloaf.voicertc.packets.UDPPacket;

public interface MultiplexedSocketClientInterface {
    boolean matchPacket(byte[] packet);
    void setParent(MultiplexedSocketParentInterface parentInterface);

    void onPacket(UDPPacket packet);
}
