package xyz.breadloaf.voicertc.webrtc.ice;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class SDPParser {
    public int mediaPort = 0;
    public String ConnectionProto = "";
    public String ConnectionIP = "";
    public HashMap<String,ArrayList<String>> attributes = new HashMap<>();
    public String SessionName = "";

    public String SessionUsername = "";
    public String SessionID = "";
    public String NetType = "";
    public String UnicastAddress = "";
    public String SessionVersion = "";
    @Nullable
    public String RawSDP = null;
    public SDPParser(String SDP) {
        ParseSDP(SDP);
    }

    private void ParseSDP(String SDP) {
        RawSDP = SDP;
        String[] SDPLines = SDP.split("((\r\n)|(\n)|(\\r\\n)|(\\n))");
        //the first line should always be a version number, and it should also always be 0
        String[] version = SDPLines[0].split("=");
        if (!version[0].trim().equals("v") && !version[1].trim().equals("0")) {
            throw new IllegalStateException("First line of SDP was not version number, or version number was not zero line was: " + SDPLines[0]);
        }
        for (String line : SDPLines) {
            String[] KeyPair = line.split("=", 2);
            if (KeyPair[0].length() != 1) {
                throw new IllegalStateException("Key in pair was not of length 1 : " + line);
            }
            switch (KeyPair[0]) { //this sdp parser is very incomplete and probably breaks spec, but it should work
                case "o":
                    ParseOrigin(KeyPair[1]);
                    break;
                case "s":
                    ParseName(KeyPair[1]);
                    break;
                case "c":
                    ParseConnectionInfo(KeyPair[1]);
                    break;
                case "a":
                    ParseAttributeKeypair(KeyPair[1]);
                    break;
                case "m":
                    ParseMediaDescriptor(KeyPair[1]);
                    break;
            }
        }
    }
    private void ParseOrigin(String cmd) {
        //o=<username> <sess-id> <sess-version> <nettype> <addrtype> <unicast-address>
        String[] data = cmd.split(" ");
        if (data.length != 6 && !"IN".equals(data[3])) {
            throw new NotImplementedException("ConnectionInfo "+cmd+" Is not implemented");
        }
        SessionUsername = data[0];
        SessionID = data[1];
        SessionVersion = data[2];
        NetType = data[3];
        UnicastAddress = data[4];
    }
    private void ParseName(String cmd) {
        // s=<name>
        SessionName=cmd;
    }
    private void ParseConnectionInfo(String cmd) {
        // c=<nettype> <addrtype> <connection-address>
        String[] data = cmd.split(" ");
        if (data.length != 3 && !"IN".equals(data[0])) {
            throw new NotImplementedException("ConnectionInfo "+cmd+" Is not implemented");
        }
        ConnectionProto = data[1];
        ConnectionIP = data[2];
    }
    private void ParseAttributeKeypair(String cmd) {
        // a=value:key OR a=value
        String[] data = cmd.split(":",2);
        if (data.length != 1) {
            ArrayList<String> arr = attributes.getOrDefault(data[0],new ArrayList<String>());
            arr.add(data[1]);
            attributes.put(data[0],arr);
        }
    }
    private void ParseMediaDescriptor(String cmd) {
        // m=<media> <port> <proto> <fmt>
        String[] data = cmd.split(" ");
        if (data.length != 4 && !"application".equals(data[0]) && !"UDP/DTLS/SCTP".equals(data[2]) && !"webrtc-datachannel".equals(data[3])) {
            throw new NotImplementedException("MediaDescriptor "+cmd+" Is not implemented");
        }
        mediaPort = Integer.parseInt(data[1]);
    }


}
