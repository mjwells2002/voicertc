package xyz.breadloaf.voicertc.webrtc;


import org.jetbrains.annotations.Nullable;

import org.bouncycastle.asn1.x500.X500Name;

import org.bouncycastle.util.encoders.Hex;

import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.io.PrintStream;

import java.net.InetSocketAddress;

import java.security.SecureRandom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import pe.pi.sctp4j.sctp.small.PooledAssociation;
import xyz.breadloaf.voicertc.api.enums.ConnectionPhase;
import xyz.breadloaf.voicertc.api.rtc.RTCUser;

import xyz.breadloaf.voicertc.VoiceRTC;
import xyz.breadloaf.voicertc.packets.UDPPacket;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;
import xyz.breadloaf.voicertc.webrtc.ice.SDPParser;

public class WebRTCUser implements RTCUser {
    public SDPParser offer;
    public SDPParser answer;
    @Nullable
    public InetSocketAddress socketAddress;
    private final ThreadRunner threadRunner;
    public static int WEBRTC_MTU = 1280; //rfc8831 "The initial Path MTU at the IP layer SHOULD NOT exceed 1200 bytes for IPv4 and 1280 bytes for IPv6."
    @Nullable public Object userData;
    private DTLSTransport dtlsTransport;
    private DTLSServerProtocol dtlsServerProtocol;
    private WebRtcDTLSServer dtlsServer;
    private RawTransport rawTransport;
    private PooledAssociation pooledAssocication;
    private ConnectionPhase phase = ConnectionPhase.HANDSHAKE;

    public WebRTCUser(String inOffer, String inAnswer, MultiplexedSocketParentInterface parentInterface, @Nullable Object userData) {
        offer = new SDPParser(inOffer);
        answer = new SDPParser(inAnswer);
        this.userData = userData;
        threadRunner = new ThreadRunner(parentInterface,this);
    }

    @Override
    public @Nullable String getOffer() {
        return offer.RawSDP;
    }

    @Override
    public @Nullable String getAnswer() {
        return answer.RawSDP;
    }

    @Override
    public @Nullable InetSocketAddress getRemoteAddress() {
        return socketAddress;
    }

    @Override
    public @Nullable Object getUserData() {
        return this.userData;
    }

    @Override
    public ConnectionPhase getPhase() {
        return phase;
    }

    public String getUsername() {
        return answer.attributes.get("ice-ufrag").get(0) + ":" + offer.attributes.get("ice-ufrag").get(0);
    }

    public void setRemoteAddress(InetSocketAddress address) {
        if (address.equals(socketAddress)) {
            return;
        }
        VoiceRTC.SocketUserMap.put(address,this);
        if (socketAddress != null) {
            VoiceRTC.SocketUserMap.remove(socketAddress);
        }
        VoiceRTC.logger.info("Network change detected for user: " + getUsername() + " new remote address "+ address);
        socketAddress = address;
    }

    public void onPacket(UDPPacket packet) {
        threadRunner.queuePacket(packet);
    }

    public static class ThreadRunner implements Runnable {

        private final MultiplexedSocketParentInterface parent;
        private final WebRTCUser parentUser;
        private final LinkedBlockingQueue<UDPPacket> incomingPackets;

        public ThreadRunner(MultiplexedSocketParentInterface parent, WebRTCUser user) {
            incomingPackets = new LinkedBlockingQueue<>();
            this.parent = parent;
            this.parentUser = user;
        }

        public void queuePacket(UDPPacket packet) {
            incomingPackets.add(packet);
            VoiceRTC.WEBRTC_THREAD_POOL_RESPECT.add(this, parentUser.getUsername());
        }

        @Override
        public void run() {
            //ArrayList<UDPPacket> packets = new ArrayList<>();
            //incomingPackets.drainTo(packets);
            UDPPacket packet = incomingPackets.poll();
            // for (UDPPacket packet : packets) {
            if (packet != null) {
                if (parentUser.phase == ConnectionPhase.HANDSHAKE) {
                    parentUser.phase = ConnectionPhase.ESTABLISHING;
                    parentUser.dtlsServerProtocol = new DTLSServerProtocol();
                    parentUser.dtlsServer = new WebRtcDTLSServer();
                    parentUser.rawTransport = new RawTransport(parent,parentUser,WEBRTC_MTU);

                    Runnable handshake = ()->{
                        try {
                            parentUser.dtlsTransport = parentUser.dtlsServerProtocol.accept(parentUser.dtlsServer,parentUser.rawTransport);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        parentUser.pooledAssocication = new PooledAssociation(VoiceRTC.WEBRTC_THREAD_POOL, VoiceRTC.WEBRTC_THREAD_POOL_SCHEDULE, parentUser.dtlsTransport, StreamEventRouter.registerAssociation(parentUser));
                        try {
                            parentUser.pooledAssocication.onPacket();
                        } catch (Exception e) {
                        }
                        parentUser.phase = ConnectionPhase.CONNECTED;
                    };
                    VoiceRTC.HANDSHAKE_THREADPOOL.execute(handshake);
                }

                parentUser.rawTransport.onPacket(packet);
                if (parentUser.pooledAssocication != null) {
                    try {
                        parentUser.pooledAssocication.onPacket();
                    } catch (Exception e) {
                        //ignored
                    }
                }

            }
        }
    }

    public static class WebRtcDTLSServer extends DefaultTlsServer {

        public WebRtcDTLSServer() {
            super(new BcTlsCrypto(new SecureRandom()));
        }

        @Override
        public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause)
        {
            PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
            out.println("DTLS server raised alert: " + AlertLevel.getText(alertLevel)
                    + ", " + AlertDescription.getText(alertDescription));
            if (message != null)
            {
                out.println("> " + message);
            }
            if (cause != null)
            {
                cause.printStackTrace(out);
            }
        }

        @Override
        public void notifyAlertReceived(short alertLevel, short alertDescription)
        {
            PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
            out.println("DTLS server received alert: " + AlertLevel.getText(alertLevel)
                    + ", " + AlertDescription.getText(alertDescription));
        }

        @Override
        public ProtocolVersion getServerVersion() throws IOException
        {
            ProtocolVersion serverVersion = super.getServerVersion();

            //System.out.println("DTLS server negotiated " + serverVersion);

            return serverVersion;
        }

        @Override
        public CertificateRequest getCertificateRequest() throws IOException
        {
            short[] certificateTypes = new short[]{ ClientCertificateType.rsa_sign };

            Vector serverSigAlgs = null;
            if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(context.getServerVersion()))
            {
                serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context);
            }

            Vector certificateAuthorities = new Vector();

            // All the CA certificates are currently configured with this subject
            certificateAuthorities.addElement(new X500Name("CN=WebRTC"));

            return new CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities);
        }

        @Override
        public void notifyClientCertificate(org.bouncycastle.tls.Certificate clientCertificate) throws IOException
        {
            TlsCertificate[] chain = clientCertificate.getCertificateList();

            //System.out.println("DTLS server received client certificate chain of length " + chain.length);
            //TODO: this?

        }

        @Override
        public void notifyHandshakeComplete() throws IOException
        {
            super.notifyHandshakeComplete();
        }

        protected String hex(byte[] data)
        {
            return data == null ? "(null)" : Hex.toHexString(data);
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions()
        {
            return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10);
        }

        protected TlsCredentialedDecryptor getRSAEncryptionCredentials() throws IOException
        {
            return TLSUtils.loadEncryptionCredentials(context, new String[]{ VoiceRTC.CERTIFICATE_FILE.getPath() }, VoiceRTC.KEY_FILE.getPath());
        }

        protected TlsCredentialedSigner getRSASignerCredentials() throws IOException
        {
            Vector clientSigAlgs = context.getSecurityParametersHandshake().getClientSigAlgs();
            return TLSUtils.loadSignerCredentials(context, new String[]{VoiceRTC.CERTIFICATE_FILE.getAbsolutePath()}, VoiceRTC.KEY_FILE.getAbsolutePath(), TlsUtils.getDefaultSignatureAlgorithm(SignatureAlgorithm.rsa));
            //return TLSUtils.loadSignerCredentialsServer(context, clientSigAlgs, SignatureAlgorithm.rsa);
        }
    }

    public static class RawTransport implements DatagramTransport {
        private final int mtu;
        private final MultiplexedSocketParentInterface parent;
        private final LinkedBlockingQueue<UDPPacket> packets;
        private final WebRTCUser user;
        private boolean isClosed = false;
        protected Runnable onPacketRead = null;

        public RawTransport(MultiplexedSocketParentInterface parentInterface, WebRTCUser user, int mtu) {
            this.parent = parentInterface;
            this.mtu = mtu;
            this.user = user;
            packets = new LinkedBlockingQueue<>();
        }

        public void onPacket(UDPPacket packet) {
            if (isClosed) { return; }
            packets.add(packet);
        }

        @Override
        public int getReceiveLimit() throws IOException {
            return mtu;
        }

        @Override
        public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
            if (isClosed) { throw new IOException("read on closed transport"); }
            try {
                UDPPacket packet = packets.poll(waitMillis,TimeUnit.MILLISECONDS);
                if (packet != null) {
                    if (packet.bytes.length > Math.min(mtu,len)) {
                        throw new IOException("Buffer too small");
                    }
                    System.arraycopy(packet.bytes,0,buf,off,Math.min(packet.bytes.length,len));
                    return Math.min(packet.bytes.length,len);
                }
                return 0;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public int getSendLimit() throws IOException {
            return mtu;
        }

        @Override
        public void send(byte[] buf, int off, int len) throws IOException {
            if (isClosed) { throw new IOException("send on closed transport"); }
            byte[] sendBuffer = new byte[len];
            System.arraycopy(buf,off,sendBuffer,0,len);
            try {
                parent.sendPacket(sendBuffer,user.socketAddress);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        public int getAvailablePackets() {
            return packets.size();
        }
        @Override
        public void close() throws IOException {
            isClosed = true;
            if (packets.size() > 0 ) {
                VoiceRTC.logger.warn("closed rawtransport with pending packets");
            }
            packets.clear();
        }
    }

}
