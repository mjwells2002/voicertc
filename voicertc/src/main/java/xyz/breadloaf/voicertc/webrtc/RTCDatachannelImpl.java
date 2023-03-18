package xyz.breadloaf.voicertc.webrtc;

import pe.pi.sctp4j.sctp.SCTPStream;
import pe.pi.sctp4j.sctp.behave.OrderedStreamBehaviour;

import xyz.breadloaf.voicertc.api.rtc.RTCDatachannel;

public class RTCDatachannelImpl implements RTCDatachannel  {
    SCTPStream sctpStream;
    Boolean isOrdered = null;

    public RTCDatachannelImpl(SCTPStream stream) {
        this.sctpStream = stream;
    }
    @Override
    public void send(byte[] data) throws Exception {
        sctpStream.send(data);
    }

    @Override
    public void send(String data) throws Exception {
        sctpStream.send(data);
    }

    @Override
    public void close() throws Exception {
        sctpStream.close();
    }

    @Override
    public boolean canSend() {
        return sctpStream.OutboundIsOpen();
    }

    @Override
    public boolean isOpen() {
        return sctpStream.InboundIsOpen() || sctpStream.OutboundIsOpen();
    }

    @Override
    public boolean isOrdered() {
        if (isOrdered == null) {
            // dear god save me from this hack
            // the field we need is private and I don't really wanna mixin this or use reflection
            // but It's in the toString ...
            String streamClass = sctpStream.getClass().getSimpleName();
            String streamString = sctpStream.toString();
            Integer sno = sctpStream.getNum();
            String label = sctpStream.getLabel();
            String orderedClass = OrderedStreamBehaviour.class.getSimpleName();
            isOrdered = streamString.startsWith(streamClass + "[" + sno + "]=" + label + "|" + orderedClass + "|->");
        }
        return isOrdered;
    }

    @Override
    public boolean isReliable() {
        return true; //TODO: need to modify the sctp4j lib to support this, is it worth it?
    }

    @Override
    public String getLabel() {
        return sctpStream.getLabel();
    }
}
