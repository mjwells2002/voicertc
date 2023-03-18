package xyz.breadloaf.voicertc.api.rtc;


import xyz.breadloaf.voicertc.api.enums.ConnectionPhase;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

public interface RTCUser {
    @Nullable
    String getOffer();
    @Nullable String getAnswer();
    @Nullable InetSocketAddress getRemoteAddress();
    @Nullable Object getUserData();
    ConnectionPhase getPhase();

}
