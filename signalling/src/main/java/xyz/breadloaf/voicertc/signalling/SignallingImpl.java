package xyz.breadloaf.voicertc.signalling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import xyz.breadloaf.voicertc.api.enums.SignallingState;
import xyz.breadloaf.voicertc.api.signalling.SignallingAPI;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SignallingImpl implements SignallingAPI {
    protected SignallingWebsocket websocket;
    protected LinkedBlockingQueue<SignallingClientImpl> processQueue = new LinkedBlockingQueue<>();
    protected URI signalingSocketUrl;
    protected String baseUrl;

    public SignallingImpl(URI signalingSocketUrl, String baseUrl) throws URISyntaxException {
        this.signalingSocketUrl = signalingSocketUrl;
        this.baseUrl = baseUrl;
        createSocket(signalingSocketUrl,baseUrl);
    }

    private void createSocket(URI signalingSocketUrl, String baseUrl) throws URISyntaxException  {
        websocket = new SignallingWebsocket(this,signalingSocketUrl,baseUrl, () -> {websocket = null;});
    }

    @Override
    public SignallingClient getClient(Function<String,String> offerProvider) {
        if(websocket == null) {
            try {
                createSocket(signalingSocketUrl,baseUrl);
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
        return new SignallingClientImpl(offerProvider,this);
    }

    protected static class SignallingClientImpl implements SignallingAPI.SignallingClient {
        private Function<String,String> answerProvider;
        private SignallingImpl parent;
        protected Consumer<SignallingState> callback;
        protected ClientURL connectionURL = null;
        protected String answer = null;
        protected String offer = null;
        protected SignallingState state = null;

        SignallingClientImpl(Function<String,String> answerProvider, SignallingImpl parent) {
            this.answerProvider = answerProvider;
            this.parent = parent;
        }

        @Override
        public @Nullable String getConnectionURL() {
            if (connectionURL == null) {
                return null;
            }
            return connectionURL.toString();
        }

        @Override
        public @Nullable String getAnswer() {
            if (answer == null) {
                answer = answerProvider.apply(this.offer);
            }
            return answer;
        }

        @Override
        public @Nullable String getOffer() {
            return offer;
        }

        @Override
        public void setOnStateChange(Consumer<SignallingState> onStateChange) {
            this.callback = onStateChange;
        }

        @Override
        public void start() {
            try {
                parent.processQueue.put(this);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        protected void setState(SignallingState newState) {
            this.state = newState;
            this.callback.accept(this.state);
        }
    }

    protected static class SignallingWebsocket extends WebSocketClient {
        protected boolean isOpen = false;
        protected ConnectionState state = ConnectionState.CLOSED;
        private final Gson gson;
        private String serverID = null;
        private Thread SignallingThread;
        private boolean threadRunning = true;
        private SignallingImpl parent;
        private final ArrayList<SignallingClientImpl> preflightUsers = new ArrayList<>();
        private final ConcurrentHashMap<String, SignallingClientImpl> inflightUsers = new ConcurrentHashMap<>();
        private boolean hasEverConnected = false;
        private final String baseURL;
        private final Runnable onFatalError;

        protected SignallingWebsocket(SignallingImpl parent, URI uri, String baseURL, Runnable onFatalError) throws URISyntaxException {
            super(uri);
            this.baseURL = baseURL;
            this.onFatalError = onFatalError;

            gson = new GsonBuilder().create();
            this.parent = parent;
            SignallingThread = new Thread(()-> {
                while (threadRunning) {
                    if (state == ConnectionState.READY) {
                        // now process stuff
                        try {
                            SignallingClientImpl toProcess = parent.processQueue.poll(200, TimeUnit.MILLISECONDS);
                            if (toProcess != null) {
                                signalUser(toProcess);
                            }
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    } else if (parent.processQueue.size() > 0) {
                        // make it ready
                        if(this.state == ConnectionState.CLOSED && hasEverConnected) {
                            this.state = ConnectionState.CONNECTING;
                            this.reconnect();
                        } else if (state == ConnectionState.CLOSED) {
                            this.state = ConnectionState.CONNECTING;
                            this.connect();
                            hasEverConnected = true;
                        }

                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        //throw new RuntimeException(e);
                    }

                }
            });

            SignallingThread.setName("Signalling Websocket Thread - rtclib");
            SignallingThread.setDaemon(true);
            SignallingThread.start();
        }
        private void signalUser(SignallingClientImpl user) {
            //should only be called when ready
            if (state != ConnectionState.READY) {
                throw new IllegalStateException("signaling user when not ready");
            }
            preflightUsers.add(user);
            OutboundMessage msg = new OutboundMessage(OutboundMessage.MessageType.ServerClientJoinRequest);
            this.send(gson.toJson(msg));
        }

        private void sendAnswer(SignallingClientImpl user) {
            if (state != ConnectionState.READY) {
                throw new IllegalStateException("answering user when not ready");
            }
            OutboundMessage msg = new OutboundMessage(OutboundMessage.MessageType.ServerClientSDPAnswer);
            msg.serverID = serverID;
            msg.userID = user.connectionURL.clientID;
            msg.sdp = new RTCLibServerPackets.SDP(true,user.getAnswer());
            this.send(gson.toJson(msg));
        }
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            //send ServerHello, wait for ServerHelloResp, then data can be sent
            sendHello();
        }

        @Override
        public void onMessage(String message) {
            RTCLibServerPackets rpkt = RTCLibServerPackets.fromJSON(message,gson);
            if (rpkt.isError) {
                //TODO: handle
                return;
            }
            if (rpkt instanceof RTCLibServerPackets.ServerHelloResp pkt) {
                if (this.state != ConnectionState.AWAIT_HELLO_RESP) {
                    //TODO: handle
                    return;
                }
                this.serverID = pkt.serverID;
                this.state = ConnectionState.READY;
            }
            if (this.state == ConnectionState.READY) {
                if (rpkt instanceof RTCLibServerPackets.ServerClientJoinResp pkt) {
                    if (preflightUsers.size() == 0) {
                        throw new IllegalStateException("no pre-flight users, when api response recv");
                    }
                    if (!this.serverID.equals(pkt.serverID)) {
                        throw new IllegalStateException("serverid != serverid from api");
                    }
                    SignallingClientImpl user = preflightUsers.remove(0);
                    inflightUsers.put(pkt.userID,user);
                    user.connectionURL = new ClientURL(pkt.serverID,pkt.userID,this.baseURL);
                    user.setState(SignallingState.URL_READY);
                } else if (rpkt instanceof RTCLibServerPackets.ServerClientJoinOfferNotify pkt) {
                    if (!this.serverID.equals(pkt.serverID)) {
                        throw new IllegalStateException("serverid != serverid from api");
                    }
                    if (inflightUsers.size() == 0) {
                        throw new IllegalStateException("no in-flight users, when api response recv");
                    }
                    SignallingClientImpl user = inflightUsers.remove(pkt.userID);
                    if (user == null) {
                        throw new IllegalStateException("user wasnt in-flight");
                    }
                    user.offer = pkt.sdp.sdp;
                    sendAnswer(user);
                    user.setState(SignallingState.SIGNALLING_COMPLETE);
                } else if (rpkt instanceof RTCLibServerPackets.ServerClientJoinTimeoutNotify pkt) {
                    SignallingClientImpl user = inflightUsers.remove(pkt.userID);
                    if (user != null) {
                        //dont care about timeouts for connected users
                        user.setState(SignallingState.TIMEOUT);
                    }
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            this.state = ConnectionState.CLOSED;
            this.isOpen = false;
        }

        @Override
        public void onError(Exception ex) {
            this.preflightUsers.removeIf((user)->{
                user.setState(SignallingState.ERROR);
                return true;
            });
            this.inflightUsers.values().removeIf((user)->{
                user.setState(SignallingState.ERROR);
                return true;
            });
            threadRunning = false;
            System.out.println("ERROR: signalling websocket error");
            onFatalError.run();
        }

        private void sendHello() {
            if (state != ConnectionState.CONNECTING) {
                return;
            }
            state = ConnectionState.AWAIT_HELLO_RESP;
            OutboundMessage msg = new OutboundMessage(OutboundMessage.MessageType.ServerHello);
            this.send(gson.toJson(msg));
        }

        private enum ConnectionState {
            READY,
            AWAIT_HELLO_RESP,
            CONNECTING,
            CLOSED
        }

    }

    protected static class ClientURL {
        protected String base_url;
        protected String serverID;
        protected String clientID;

        protected ClientURL(String serverID, String clientID, String base_url) {
            this.serverID = serverID;
            this.clientID = clientID;
            this.base_url = base_url;
        }

        @Override
        public String toString() {
            return base_url + "?s=" + serverID + "&c=" + clientID;
        }
    }
}
