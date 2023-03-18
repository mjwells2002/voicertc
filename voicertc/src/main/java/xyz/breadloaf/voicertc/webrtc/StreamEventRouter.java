package xyz.breadloaf.voicertc.webrtc;

import org.jetbrains.annotations.Nullable;

import pe.pi.sctp4j.sctp.*;

import xyz.breadloaf.voicertc.VoiceRTC;
import xyz.breadloaf.voicertc.api.WebRTC;
import xyz.breadloaf.voicertc.api.enums.ErrorType;
import xyz.breadloaf.voicertc.api.rtc.StreamEvents;
import xyz.breadloaf.voicertc.api.signalling.SignallingAPI;
import xyz.breadloaf.voicertc.webrtc.ice.SDPGenerator;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class StreamEventRouter {
    public static ConcurrentHashMap<String,WebRTCUser> Users = new ConcurrentHashMap<>();
    protected static final ConcurrentHashMap<String, StreamEvents> streamEventMap = new ConcurrentHashMap<>();

    public static AssociationListener registerAssociation(WebRTCUser user) {
        //TODO: setup some kind of timeout for these
        return new UserAssociation(user);
    }

    //test function
    public static WebRTC getApi(String modid) { return new ApiImpl(modid); }

    protected static class ApiImpl implements WebRTC {
        protected final ExecutorService ApiPool = Executors.newFixedThreadPool(4);
        protected final ExecutorService ApiSchedulePool = Executors.newScheduledThreadPool(1);
        protected SignallingAPI signallingAPI = null;
        protected final String prefix;
        protected ApiImpl(String prefix) {
            this.prefix = prefix;
        }

        private String prefixName(String name) { return this.prefix + ":" + name; }

        @Override
        public void setSignallingAPI(SignallingAPI signallingAPI) {
            this.signallingAPI = signallingAPI;
        }

        @Override
        public void registerListener(String streamName, StreamEvents listener) {
            streamEventMap.put(prefixName(streamName),listener);
        }

        @Override
        public void deregisterListener(String streamName, StreamEvents listener) {
            streamEventMap.remove(prefixName(streamName),listener);
        }

        @Override
        public void getConnectionURL(Consumer<String> success, Consumer<ErrorType> failed, @Nullable Object data) {
            if (this.signallingAPI == null) {
                throw new IllegalStateException("getting url with no signalling api!");
            }
            SignallingAPI.SignallingClient client = this.signallingAPI.getClient((offer)->{
                try {
                    return SDPGenerator.generateSDP((InetSocketAddress) VoiceRTC.ParentSocket.socket.getLocalSocketAddress(),offer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            client.setOnStateChange((state) -> {
                switch(state) {
                    case TIMEOUT -> {
                        failed.accept(ErrorType.USER_TIMEOUT);
                    }
                    case SIGNALLING_COMPLETE -> {
                        WebRTCUser user = new WebRTCUser(client.getOffer(), client.getAnswer(), VoiceRTC.ParentSocket,data);
                        Users.put(user.getUsername(),user);
                    }
                    case URL_READY -> {
                        success.accept(client.getConnectionURL());
                    }
                    case ERROR -> {
                        failed.accept(ErrorType.UNKNOWN_ERROR);
                    }
                }
            });
            client.start();
        }
    }
    protected static class StreamListener implements SCTPStreamListener, SCTPByteStreamListener {
        protected final SCTPStream stream;
        protected final WebRTCUser user;

        protected StreamListener(WebRTCUser user, SCTPStream stream) {
            stream.setSCTPStreamListener(this);

            this.stream = stream;
            this.user = user;
            StreamEvents evt = streamEventMap.get(stream.getLabel());
            if (evt != null) {
                evt.onStreamOpen(user,new RTCDatachannelImpl(stream));
            }
        }

        @Override
        public void onMessage(SCTPStream sctpStream, String s) {
            StreamEvents evt = streamEventMap.get(stream.getLabel());
            if (evt != null) {
                evt.onStreamMessage(user,new RTCDatachannelImpl(stream),s);
            }
        }

        @Override
        public void onMessage(SCTPStream sctpStream, byte[] bytes) {
            StreamEvents evt = streamEventMap.get(stream.getLabel());
            if (evt != null) {
                evt.onStreamMessage(user,new RTCDatachannelImpl(stream),bytes);
            }
        }

        @Override
        public void close(SCTPStream sctpStream) {
            StreamEvents evt = streamEventMap.get(stream.getLabel());
            if (evt != null) {
                evt.onStreamClose(user, new RTCDatachannelImpl(stream));
            }
        }
    }

    protected static class UserAssociation implements AssociationListener {
        protected final WebRTCUser user;
        @Nullable
        protected Association association = null;

        protected UserAssociation(WebRTCUser user) {
            this.user = user;
        }

        @Override
        public void onAssociated(Association association) {
            this.association = association;
        }

        @Override
        public void onDisAssociated(Association association) {
            this.association = null;
        }

        @Override
        public void onDCEPStream(SCTPStream sctpStream, String s, int i) throws Exception {
            new StreamListener(user,sctpStream);
        }

        @Override
        public void onRawStream(SCTPStream sctpStream) {
            //ignore
        }
    }
}
