package xyz.breadloaf.voicertc.api;

import xyz.breadloaf.voicertc.api.enums.ErrorType;
import xyz.breadloaf.voicertc.api.rtc.StreamEvents;
import xyz.breadloaf.voicertc.api.signalling.SignallingAPI;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public interface WebRTC {
    void setSignallingAPI(SignallingAPI signallingAPI);
    void registerListener(String streamName, StreamEvents listener);
    void deregisterListener(String streamName, StreamEvents listener);
    void getConnectionURL(Consumer<String> success, Consumer<ErrorType> failed, @Nullable Object data);

}
