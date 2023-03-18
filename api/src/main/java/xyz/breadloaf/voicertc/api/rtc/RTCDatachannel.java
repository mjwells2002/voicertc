package xyz.breadloaf.voicertc.api.rtc;

public interface RTCDatachannel {
    void send(byte[] data) throws Exception;
    void send(String data) throws Exception;
    void close() throws Exception;
    boolean canSend();
    boolean isOpen();
    boolean isOrdered();
    boolean isReliable();
    String getLabel();
}
