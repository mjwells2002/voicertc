package xyz.breadloaf.voicertc.sockets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.breadloaf.voicertc.packets.UDPPacket;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketClientInterface;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;
import xyz.breadloaf.voicertc.webrtc.DTLSPacketHandler;
import xyz.breadloaf.voicertc.webrtc.StreamEventRouter;
import xyz.breadloaf.voicertc.webrtc.WebRTCUser;
import xyz.breadloaf.voicertc.webrtc.ice.IceUtils;
import xyz.breadloaf.voicertc.webrtc.stun.StunUtils;

import java.net.InetSocketAddress;

public class RTCMultiplexClientSocket implements MultiplexedSocketClientInterface, StunUtils.StunEvents {
    Logger logger = LogManager.getLogger("VoiceRTC RTC Socket");
    MultiplexedSocketParentInterface parentSocket;
    public StunUtils stunUtils;
    DTLSPacketHandler dtlsPacketHandler;

    @Override
    public boolean matchPacket(byte[] packet) {
        return packet[0] != -1;
    }

    @Override
    public void setParent(MultiplexedSocketParentInterface parentInterface) {
        this.parentSocket = parentInterface;
        this.stunUtils = new StunUtils(this,this,parentSocket);
        this.dtlsPacketHandler = new DTLSPacketHandler(this,parentSocket);
    }

    @Override
    public void onPacket(UDPPacket packet) {
        if ((packet.bytes[0] & 0xff) < 2) {
            //STUN
            stunUtils.onStunPacket(packet);
        } else if ((packet.bytes[0] & 0xff) > 19 && (packet.bytes[0] & 0xff) < 64) {
            //DTLS
            dtlsPacketHandler.onDTLSPacket(packet);
        } else if ((packet.bytes[0] & 0xff) > 127 && (packet.bytes[0] & 0xff) < 192) {
            //RTP
            logger.info("RTP Packet");
        }
    }


    @Override
    public boolean validateUfrag(String ufrag, InetSocketAddress socketAddress) {
        WebRTCUser user = StreamEventRouter.Users.get(getOwnUserfragment() + ":" + ufrag);
        if (user != null) {
            user.setRemoteAddress(socketAddress);
            return true;
        }
        return false;
    }

    @Override
    public String getOwnPWD() {
        return IceUtils.ServerPWD;
    }

    @Override
    public String getOwnUserfragment() {
        return IceUtils.ServerUfrag;
    }
}
