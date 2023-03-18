package xyz.breadloaf.voicertc.api;

public interface RTCEntrypoint {
    String getID();
    void initialize(WebRTC api) throws Throwable;
}
