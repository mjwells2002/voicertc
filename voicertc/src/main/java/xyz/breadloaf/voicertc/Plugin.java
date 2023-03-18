package xyz.breadloaf.voicertc;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoiceHostEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent;
import xyz.breadloaf.voicertc.voicechatapi.VoicechatEvents;

public class Plugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "VoiceRTC";
    }

    @Override
    public void initialize(VoicechatApi api) {

    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartingEvent.class, VoicechatEvents::VoicechatServerStartingEvent);
        registration.registerEvent(VoicechatServerStartedEvent.class, VoicechatEvents::VoicechatServerStartedEvent);
        registration.registerEvent(VoiceHostEvent.class, VoicechatEvents::VoicechatVoiceHostEvent);
    }
}
