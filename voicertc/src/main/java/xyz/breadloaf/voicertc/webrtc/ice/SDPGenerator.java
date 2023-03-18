package xyz.breadloaf.voicertc.webrtc.ice;

import xyz.breadloaf.voicertc.VoiceRTC;

import java.net.*;

public class SDPGenerator {
    /*
v=0
o=- 4801234539622056621 0 IN IP4 0.0.0.0
s=-
t=0 0
a=sendrecv
a=msid-semantic: WMS lgsCFqt9kN2fVKw5wg3NKqGdATQoltEwOdMS
a=fingerprint:sha-256 68:4B:FF:01:68:8C:BF:19:20:CA:93:F1:C7:B0:92:7B:40:A5:68:52:8E:A2:A9:D7:A5:E0:95:AE:2F:F1:96:5E
a=setup:passive
a=group:BUNDLE 0
a=msid-semantic:WMS *
m=application 24454 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 192.168.0.233
a=candidate:1 1 UDP 1686052351 192.168.0.150 24454 typ srflx raddr 0.0.0.0 rport 0
a=sendrecv
a=end-of-candidates
a=ice-pwd:d213292da542bf533e275a49e1990b5f
a=ice-ufrag:68e38401
a=mid:0
a=sctp-port:5000
a=max-message-size:1073741823
     */


    /*
v=0
o=- 8714654958384213934 2 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0
a=extmap-allow-mixed
a=msid-semantic: WMS
m=application 61894 UDP/DTLS/SCTP webrtc-datachannel
c=IN IP4 192.168.64.47
a=candidate:3640549380 1 udp 2122260223 192.168.64.47 61894 typ host generation 0 network-id 1 network-cost 10
a=candidate:1043282249 1 udp 2122199807 fc00:bbbb:bbbb:bb01::2f:f12c 51106 typ host generation 0 network-id 4 network-cost 50
a=candidate:87156214 1 udp 2122129151 100.109.154.29 64792 typ host generation 0 network-id 2 network-cost 50
a=candidate:3574799458 1 udp 2122063615 10.110.241.45 51429 typ host generation 0 network-id 3 network-cost 50
a=ice-lite
a=ice-ufrag:tXkR
a=ice-pwd:h6XachG/+fNxhflptNqzTNyN
a=fingerprint:sha-256 93:E5:CC:CE:31:E9:BC:FC:80:96:FA:74:9B:1E:3C:3D:74:AA:5C:CE:CD:52:1D:48:06:E6:0E:FB:38:32:8D:F2
a=setup:passive
a=mid:0
a=sctp-port:5000
a=max-message-size:262144
     */
    public static String lineEnding = "\r\n";
    public static String compressIPV6Address(Inet6Address inet6Address) {
        String longAddress = inet6Address.getHostAddress().replaceFirst("%.*","");
        return longAddress.replaceFirst("(^|:)(0+(:|$)){2,8}", "::");
    }

    public static void getLocalIceCandidates(StringBuilder candidateOut, int port) throws SocketException {
        //TODO: add in correct formula for calculating foundation and priority
        var netInterfaces = NetworkInterface.getNetworkInterfaces();
        while (netInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = netInterfaces.nextElement();
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                generateCandidateFor(interfaceAddress.getAddress(),candidateOut,port);
            }
        }
    }
    public static void getCustomIceCandidates(String host_with_port, StringBuilder candidateOut, int defaultPort) throws URISyntaxException, UnknownHostException {
        URI customURI = new URI("voicertc://" + host_with_port);
        InetAddress[] ips = InetAddress.getAllByName(customURI.getHost());
        int port = customURI.getPort();
        if (port < 0) {
            port = defaultPort;
        }
        for (InetAddress ip : ips) {
            generateCandidateFor(ip,candidateOut,port);
        }
    }
    public static void generateCandidateFor(InetAddress inetAddress, StringBuilder candidateOut, int port){
        if (inetAddress instanceof Inet6Address inet6Address && VoiceRTC.CONFIG.AllowIPV6Candidates.get()) {
            if (!inet6Address.isLoopbackAddress() || VoiceRTC.CONFIG.AllowLocalhostCandidates.get()) {
                candidateOut.append("a=candidate:3640549380 1 udp 2122260223 %s %d typ host".formatted(compressIPV6Address(inet6Address),port));
                candidateOut.append(lineEnding);
            }
        } else if (inetAddress instanceof Inet4Address inet4Address && VoiceRTC.CONFIG.AllowIPV4Candidates.get()) {
            if (!inet4Address.isLoopbackAddress() || VoiceRTC.CONFIG.AllowLocalhostCandidates.get()) {
                candidateOut.append("a=candidate:3640549380 1 udp 2122260223 %s %d typ host".formatted(inet4Address.getHostAddress(),port));
                candidateOut.append(lineEnding);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    public static String generateSDP(InetSocketAddress socketAddress, String offer) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("v=0");
        stringBuilder.append(lineEnding);

        //TODO: generate session id
        stringBuilder.append("o=- 8714654958384213934 2 IN IP4 0.0.0.0");
        stringBuilder.append(lineEnding);

        stringBuilder.append("s=-");
        stringBuilder.append(lineEnding);

        stringBuilder.append("t=0 0");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=group:BUNDLE 0");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=extmap-allow-mixed");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=msid-semantic: WMS");
        stringBuilder.append(lineEnding);

        stringBuilder.append("m=application %d UDP/DTLS/SCTP webrtc-datachannel".formatted(socketAddress.getPort()));
        stringBuilder.append(lineEnding);

        stringBuilder.append("c=IN IP4 0.0.0.0");
        stringBuilder.append(lineEnding);

        //ice candidates
        if (VoiceRTC.CONFIG.AllowLocalInterfaces.get()) {
            getLocalIceCandidates(stringBuilder, socketAddress.getPort());
        }
        if (VoiceRTC.CONFIG.AllowVoiceHost.get() && !VoiceRTC.VOICE_HOST.equals("")) {
            getCustomIceCandidates(VoiceRTC.VOICE_HOST,stringBuilder,socketAddress.getPort());
        }
        for (InetSocketAddress inetSocketAddress : VoiceRTC.stunAddresses) {
            getCustomIceCandidates(inetSocketAddress.toString().replace("/",""),stringBuilder,socketAddress.getPort());
        }
        if (!"".equals(VoiceRTC.CONFIG.CustomRTCAddress.get())) {
            getCustomIceCandidates(VoiceRTC.CONFIG.CustomRTCAddress.get(),stringBuilder, socketAddress.getPort());
        }


        stringBuilder.append("a=ice-lite");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=ice-ufrag:").append(IceUtils.ServerUfrag);
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=ice-pwd:").append(IceUtils.ServerPWD);
        stringBuilder.append(lineEnding);

        //EX: a=fingerprint:sha-256 93:E5:CC:CE:31:E9:BC:FC:80:96:FA:74:9B:1E:3C:3D:74:AA:5C:CE:CD:52:1D:48:06:E6:0E:FB:38:32:8D:F2
        stringBuilder.append("a=fingerprint:sha-256 ");
        VoiceRTC.writeFingerprint(stringBuilder);
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=setup:passive");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=mid:0");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=sctp-port:5000");
        stringBuilder.append(lineEnding);

        stringBuilder.append("a=max-message-size:262144");
        stringBuilder.append(lineEnding);

        return stringBuilder.toString();
    }
}
