package xyz.breadloaf.voicertc.voicechatapi;

import de.maxhenkel.voicechat.api.events.VoiceHostEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent;
import xyz.breadloaf.voicertc.VoiceRTC;
import xyz.breadloaf.voicertc.sockets.RTCMultiplexClientSocket;
import xyz.breadloaf.voicertc.sockets.UDPMultiplexParentSocket;

public class VoicechatEvents {
    public static void VoicechatServerStartingEvent(VoicechatServerStartingEvent event) {
        UDPMultiplexParentSocket parentSocket = new UDPMultiplexParentSocket();
        VoicechatSocketMultiplexClient voicechatInterface = new VoicechatSocketMultiplexClient();
        parentSocket.addClient(voicechatInterface,true);
        VoiceRTC.ParentSocket = parentSocket;
        event.setSocketImplementation(voicechatInterface);
    }

    public static void VoicechatServerStartedEvent(VoicechatServerStartedEvent event) {
        VoiceRTC.clientSocket = new RTCMultiplexClientSocket();
        VoiceRTC.ParentSocket.addClient(VoiceRTC.clientSocket, false);
        VoiceRTC.RTCAvailable();
    }

    public static void VoicechatVoiceHostEvent(VoiceHostEvent event) {
        VoiceRTC.VOICE_HOST = event.getVoiceHost();
    }
}
