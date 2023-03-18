package xyz.breadloaf.voicertc.example;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xyz.breadloaf.voicertc.api.RTCEntrypoint;
import xyz.breadloaf.voicertc.api.WebRTC;
import xyz.breadloaf.voicertc.api.rtc.RTCDatachannel;
import xyz.breadloaf.voicertc.api.rtc.RTCUser;
import xyz.breadloaf.voicertc.api.rtc.StreamEvents;
import xyz.breadloaf.voicertc.signalling.SignallingImpl;

import java.net.URI;
import java.nio.charset.StandardCharsets;

public class RTCMain implements RTCEntrypoint {

    public static WebRTC rtcAPI = null;

    @Override
    public String getID() {
        return "test";
    }

    @Override
    public void initialize(WebRTC rtcAPI) throws Throwable {
        RTCMain.rtcAPI = rtcAPI;
        rtcAPI.setSignallingAPI(new SignallingImpl(new URI("wss://rtclib-api.curve.breadloaf.xyz"),"https://s3.breadloaf.xyz/test2/index.html"));

        rtcAPI.registerListener("echo", new StreamEvents() {
            @Override
            public void onStreamOpen(RTCUser user, RTCDatachannel stream) { }
            @Override
            public void onStreamMessage(RTCUser user, RTCDatachannel stream, byte[] message) {
                try {
                    //ServerPlayer player = (ServerPlayer) user.getUserData();
                    //assert player != null;
                    //player.sendSystemMessage(Component.literal(new String(message, StandardCharsets.UTF_8)));
                    //stream.send(message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            @Override
            public void onStreamMessage(RTCUser user, RTCDatachannel stream, String message) {
                try {
                    //ServerPlayer player = (ServerPlayer) user.getUserData();
                    //assert player != null;
                    //player.sendSystemMessage(Component.literal(message));
                    //stream.send(message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            @Override
            public void onStreamClose(RTCUser user, RTCDatachannel stream) { }
        });

    }
}
