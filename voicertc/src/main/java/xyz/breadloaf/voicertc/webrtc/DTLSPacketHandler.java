package xyz.breadloaf.voicertc.webrtc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import xyz.breadloaf.voicertc.VoiceRTC;

import xyz.breadloaf.voicertc.packets.UDPPacket;

import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketClientInterface;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;

import java.net.InetSocketAddress;

public class DTLSPacketHandler {
    MultiplexedSocketClientInterface client;
    MultiplexedSocketParentInterface parent;

    public DTLSPacketHandler(MultiplexedSocketClientInterface clientInterface, MultiplexedSocketParentInterface parentInterface) {
        client = clientInterface;
        parent = parentInterface;
    }

    public void onDTLSPacket(UDPPacket packet) {
        if (packet.socketAddress instanceof InetSocketAddress socketAddress) {
            WebRTCUser user = VoiceRTC.SocketUserMap.get(socketAddress);
            if (user != null) {
                user.onPacket(packet);
            }
        }
    }
}
