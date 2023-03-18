package xyz.breadloaf.voicertc.api.rtc;

public interface StreamEvents {
    void onStreamOpen(RTCUser user, RTCDatachannel stream);
    void onStreamMessage(RTCUser user, RTCDatachannel stream, byte[] message);
    void onStreamMessage(RTCUser user, RTCDatachannel stream, String message);
    void onStreamClose(RTCUser user, RTCDatachannel stream);
}
