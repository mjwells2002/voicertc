package xyz.breadloaf.voicertc.signalling;

import com.google.gson.Gson;

public class RTCLibServerPackets {
    public boolean isError = true;
    public String error;
    public String type;

    public static RTCLibServerPackets fromJSON(String json, Gson gson) {
        RTCLibServerPackets pkt = gson.fromJson(json, RTCLibServerPackets.class);
        if ("ServerHelloResp".equals(pkt.type)) {
            return gson.fromJson(json, ServerHelloResp.class);
        } else if ("ServerClientJoinResp".equals(pkt.type)) {
            return gson.fromJson(json, ServerClientJoinResp.class);
        } else if ("ServerClientJoinOfferNotify".equals(pkt.type)) {
            return gson.fromJson(json, ServerClientJoinOfferNotify.class);
        } else if ("ServerClientJoinTimeoutNotify".equals(pkt.type)) {
            return gson.fromJson(json, ServerClientJoinTimeoutNotify.class);
        }
        return pkt;
    }
    public class ServerHelloResp extends RTCLibServerPackets {
        public String serverID;
    }
    public class ServerClientJoinResp extends RTCLibServerPackets {
        public String serverID;
        public String userID;
        public int timeout_seconds;
    }
    public class ServerClientJoinOfferNotify extends RTCLibServerPackets {
        public String serverID;
        public String userID;
        public SDP sdp;
    }
    public class ServerClientJoinTimeoutNotify extends RTCLibServerPackets {
        public String serverID;
        public String userID;
    }
    public static class SDP {
        public String type;
        public String sdp;

        public SDP(boolean isAnswer, String sdp) {
            this.sdp = sdp;
            this.type = isAnswer ? "answer" : "offer";
        }
    }
}



