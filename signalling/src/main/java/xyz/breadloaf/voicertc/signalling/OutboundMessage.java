package xyz.breadloaf.voicertc.signalling;


import javax.annotation.Nullable;

public class OutboundMessage {
    public String type;
    @Nullable
    public String serverID;
    @Nullable
    public String userID;
    @Nullable
    public RTCLibServerPackets.SDP sdp;

    public OutboundMessage(MessageType type) {
        this.type = type.name();
    }
    public enum MessageType {
        ServerHello,
        ServerClientJoinRequest,
        ServerClientSDPAnswer,
    }
}
