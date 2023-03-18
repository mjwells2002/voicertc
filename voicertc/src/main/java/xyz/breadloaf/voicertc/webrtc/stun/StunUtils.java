package xyz.breadloaf.voicertc.webrtc.stun;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import xyz.breadloaf.voicertc.VoiceRTC;
import xyz.breadloaf.voicertc.packets.UDPPacket;

import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketClientInterface;
import xyz.breadloaf.voicertc.sockets.interfaces.MultiplexedSocketParentInterface;
import xyz.breadloaf.voicertc.webrtc.ice.IceUtils;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public class StunUtils {
    StunEvents evt;
    MultiplexedSocketClientInterface client;
    MultiplexedSocketParentInterface parent;
    HashMap<StunTransactionID, Consumer<InetSocketAddress>> pendingOutboundBindingRequests = new HashMap<>();
    public StunUtils(StunEvents evt, MultiplexedSocketClientInterface clientInstance, MultiplexedSocketParentInterface parent) {
        this.evt = evt;
        this.client = clientInstance;
        this.parent = parent;
    }

    public byte[] doHmacSha1(byte[] key, byte[] data) {
        Digest digest = new SHA1Digest();

        HMac hMac = new HMac(digest);
        hMac.init(new KeyParameter(key));

        hMac.update(data, 0, data.length);
        byte[] hmacOut = new byte[hMac.getMacSize()];

        hMac.doFinal(hmacOut, 0);
        return hmacOut;
    }

    public void onStunPacket(UDPPacket packet) {
        ByteBuf buf = Unpooled.wrappedBuffer(packet.bytes);
        //packet must be atleast 20 bytes for the header
        if (buf.readableBytes() < 20) {
            return;
        }
        short msgType = buf.readShort();
        short msgLen = buf.readShort();
        int magic = buf.readInt(); // must be 0x2112a442
        if (magic != 0x2112a442) {
            return;
        }
        ByteBuf transactionID = buf.readBytes(12);
        ByteBuf payload = buf.readBytes(msgLen);
        Set<StunTransactionID> keySet = pendingOutboundBindingRequests.keySet();
        StunTransactionID[] ids = keySet.toArray(new StunTransactionID[0]);
        for (StunTransactionID pendingTransactionID : ids) {
            if (pendingTransactionID.compare(transactionID)) {
                //This is a response to a binding request we sent
                HashMap<Short, ByteBuf> attributes = readAttributes(payload);
                if (msgType == (short)0x0101) { // binding success response
                    ByteBuf mapped = attributes.getOrDefault((short)0x0001,null);
                    ByteBuf xorMapped = attributes.getOrDefault((short)0x0020,null);
                    if (xorMapped != null) {
                        short reserved_ipfamily = xorMapped.readShort();
                        short port = (short) (xorMapped.readShort() ^ 0x2112);
                        byte[] xaddr = null;
                        if (reserved_ipfamily == 0x0001) {
                            xaddr = new byte[4];
                            xorMapped.readBytes(xaddr);
                        } else if (reserved_ipfamily == 0x0002) {
                            xaddr = new byte[16];
                            xorMapped.readBytes(xaddr);
                        }
                        byte[] ipMagic = new byte[16];
                        byte[] magicCookie = new byte[] { (byte) 0x21,(byte) 0x12,(byte) 0xa4,(byte) 0x42 };
                        transactionID.resetReaderIndex();
                        transactionID.readBytes(ipMagic,4,12);
                        transactionID.resetReaderIndex();
                        System.arraycopy(magicCookie,0,ipMagic,0,4);
                        if (xaddr == null) { return; }
                        byte[] addr = new byte[xaddr.length];
                        for (int i = 0; i<xaddr.length; i++) {
                            addr[i] = (byte) (xaddr[i] ^ ipMagic[i]);
                        }
                        try {
                            InetAddress address = InetAddress.getByAddress(addr);
                            pendingOutboundBindingRequests.get(pendingTransactionID).accept(new InetSocketAddress(address,port));
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (mapped != null) {
                        short reserved_ipfamily = mapped.readShort();
                        short port = mapped.readShort();
                        byte[] addr = null;
                        if (reserved_ipfamily == 0x0001) {
                            addr = new byte[4];
                            mapped.readBytes(addr);
                        } else if (reserved_ipfamily == 0x0002) {
                            addr = new byte[16];
                            mapped.readBytes(addr);
                        }
                        try {
                            InetAddress address = InetAddress.getByAddress(addr);
                            pendingOutboundBindingRequests.get(pendingTransactionID).accept(new InetSocketAddress(address,port));
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    pendingOutboundBindingRequests.remove(pendingTransactionID);
                } else { //something failed
                    VoiceRTC.logger.warn("Stun Client got unexpected response from server");
                    pendingOutboundBindingRequests.remove(pendingTransactionID);
                }
                return;
            }
        }

        if (msgType == (short)0x0001) {
            //BindingRequest
            HashMap<Short, ByteBuf> attributes = readAttributes(payload);
            ByteBuf empty = Unpooled.buffer(0,0);
            boolean isHMACSHA1 = attributes.getOrDefault((short) 0x0008,empty).readableBytes() == 20;
            boolean hasFingerprint = attributes.getOrDefault((short) 0x8028,empty).readableBytes() == 4;
            InetSocketAddress socketAddress;
            if (packet.socketAddress instanceof InetSocketAddress inetSocketAddress) {
                socketAddress = inetSocketAddress;
            } else {
                return;
            }
            String username = attributes.getOrDefault((short) 0x0006,empty).toString(StandardCharsets.UTF_8);
            String selfUserFragment = evt.getOwnUserfragment();
            if (!username.contains(selfUserFragment) && !username.contains(":"))
                return;
            String[] fragments = username.split(":");
            String remoteUserFragment = fragments[0].equals(selfUserFragment) ? fragments[1] : fragments[0];

            //TODO: validate hmac-sha1 message integrity if present
            //TODO: validate fingerprint here

            if (evt.validateUfrag(remoteUserFragment,socketAddress)) {
                //create response
                ByteBuf response = Unpooled.buffer(20);

                byte[] ip = socketAddress.getAddress().getAddress();
                boolean isIPV6 = ip.length != 4;
                //length of attributes
                short length = 0;
                length += ip.length + 2 + 2 + 1 + 1 + 2; // XOR Mapped Address Attribute (2 byte type / 2 byte length / ip bytes / ip flag / reserved byte / port)
                if (isHMACSHA1)
                    length += 20 + 2 + 2; // Message Integrity Attribute (2 byte type / 2 byte length / 20 byte hmac-sha1

                response.writeShort((short) 0x0101); // Binding Success Response
                response.writeShort(length); // as per rfc this length must contain the Message Integrity Attribute when it is used to calculate hmac-sha1
                response.writeInt(0x2112a442); //magic cookie must be 2112a442 (1118048801)
                response.writeBytes(transactionID); // 12 bytes header is now complete

                // XOR MAPPED Address
                // magic is hardcoded as 0x2112a442 so the most significant  16 bits are 0x2112
                // im sorry for this mess
                short Xport = (short) (socketAddress.getPort() ^ 0x2112);
                byte[] ipMagic = new byte[16];
                byte[] magicCookie = new byte[] { (byte) 0x21,(byte) 0x12,(byte) 0xa4,(byte) 0x42 };
                transactionID.resetReaderIndex();
                transactionID.readBytes(ipMagic,4,12);
                transactionID.resetReaderIndex();
                System.arraycopy(magicCookie,0,ipMagic,0,4);
                response.writeShort((short) 0x0020); // type
                response.writeShort(ip.length+4); // size (always 4 bytes ontop of ip length)
                response.writeByte((byte)0x00); // reserved always 0x00
                response.writeByte(isIPV6 ? (byte) 0x02 : (byte) 0x01); // protocol flag
                response.writeShort(Xport); // port number
                for (int i = 0; i<ip.length; i++) {
                    response.writeByte(ip[i] ^ ipMagic[i]);
                }


                if (isHMACSHA1) {
                    //Message Integrity Attribute
                    byte[] packetToHmac = new byte[response.readableBytes()];
                    response.readBytes(packetToHmac,0,response.readableBytes());
                    response.resetReaderIndex();
                    //TODO: own PWD

                    byte[] hmac = doHmacSha1(evt.getOwnPWD().getBytes(),packetToHmac);

                    if (hmac.length != 20)
                        return;
                    response.writeShort((short) 0x0008); // type
                    response.writeShort(hmac.length); // length
                    response.writeBytes(hmac); // hmac of all data before this attribute with length pointing to it
                }

                if (hasFingerprint) {
                    /*
                        The FINGERPRINT attribute MAY be present in all STUN messages.

                       The value of the attribute is computed as the CRC-32 of the STUN
                       message up to (but excluding) the FINGERPRINT attribute itself,
                       XOR'ed with the 32-bit value 0x5354554e.
                    */
                    // For chrome to work we have to add a fingerprint if it was provided
                    response.markWriterIndex();
                    response.writerIndex(2);
                    response.writeShort(length += 8);
                    response.resetWriterIndex();
                    byte[] packetToCRC32 = new byte[response.readableBytes()];
                    response.readBytes(packetToCRC32,0,response.readableBytes());
                    response.resetReaderIndex();
                    CRC32 crc = new CRC32();
                    crc.update(packetToCRC32);
                    long CRC = crc.getValue();
                    long XCRC = CRC ^ 0x5354554e;
                    response.writeShort(0x8028);
                    response.writeShort(4);
                    response.writeInt((int) XCRC);
                }

                try {
                    byte[] toSend = new byte[response.readableBytes()];
                    response.readBytes(toSend,0,response.readableBytes());
                    response.resetReaderIndex();
                    parent.sendPacket(toSend,socketAddress);
                } catch (Exception e) {
                    //ignore
                }
            }
        }
    }

    /* recursive function, will attempt to use all stun servers on all protocols, consumer may be ran multiple times */
    public void getOwnAddress(Consumer<InetSocketAddress> addressConsumer) {
        getOwnAddress(addressConsumer,0);
    }
    public void getOwnAddress(Consumer<InetSocketAddress> addressConsumer, int restartCount) {
        String[] stunServers = VoiceRTC.CONFIG.StunServer.get().split(";");
        for (String stunServer : stunServers) {
            URI stunServerURI = null;
            try {
                stunServerURI = new URI("stun://" + stunServer);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            if (stunServerURI.getPort() < 0) { return; }
            InetAddress[] stunIPs = new InetAddress[0];
            try {
                stunIPs = InetAddress.getAllByName(stunServerURI.getHost());
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            for (InetAddress ip : stunIPs) {
                StunTransactionID stunTransactionID = new StunTransactionID();
                ByteBuf stunBindingRequest = Unpooled.buffer(20);
                stunBindingRequest.writeShort((short)0x0001); //type
                stunBindingRequest.writeShort((short)0x0000); //payload length
                stunBindingRequest.writeInt(0x2112a442); //cookie, must be 2112a442
                stunBindingRequest.writeBytes(stunTransactionID.bytes);
                try {
                    parent.sendPacket(stunBindingRequest.array(),new InetSocketAddress(ip,stunServerURI.getPort()));
                    pendingOutboundBindingRequests.put(stunTransactionID, addressConsumer);
                    VoiceRTC.WEBRTC_THREAD_POOL_SCHEDULE.schedule(() -> {
                        if (pendingOutboundBindingRequests.containsKey(stunTransactionID) && !VoiceRTC.stunFailed) {
                            //request has timed out and failed :(
                            pendingOutboundBindingRequests.remove(stunTransactionID);
                            if (pendingOutboundBindingRequests.size() == 0 && restartCount <= 5) {
                                //all outbound requests have failed, we need to restart
                                getOwnAddress(addressConsumer,restartCount+1);
                            } else {
                                // 5 retry, give up
                                VoiceRTC.logger.error("Stun Client failed after 5 attempts");
                                VoiceRTC.stunFailed = true;
                            }
                        }
                    },5, TimeUnit.SECONDS);

                } catch (Exception e) {
                    VoiceRTC.logger.info("Stun client failed, server: "+ip.toString());
                }
            }
        }

    }
    private HashMap<Short, ByteBuf> readAttributes(ByteBuf attrib) {
        HashMap<Short, ByteBuf> ret = new HashMap<>();
        while (attrib.readableBytes() > 4) {
            // Attributes are 4 byte aligned so we also need to work out padding length
            //4 bytes is the minimum attribute length (2 byte id + 2 byte length of 0)
            short type = attrib.readShort();
            short length = attrib.readShort();
            ByteBuf payload = attrib.readBytes(length);
            int paddingSize = 4 - ((2 + 2 + length) % 4);
            if (paddingSize != 4) { //this is actually bytes to next 4 byte interval so if its 4 its already aligned and not padded
                attrib.readBytes(paddingSize); // this is the padding dont care about value just need to correct the indexs
            }
            ret.put(type,payload);
        }
        return ret;
    }

    public static class StunTransactionID {
        byte[] bytes;
        public StunTransactionID() {
            bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
        }
        public StunTransactionID(byte[] bytes) {
            this.bytes = bytes;
        }
        public boolean compare(byte[] inbytes) {
            return Arrays.equals(this.bytes, inbytes);
        }
        public boolean compare(ByteBuf inbytes) {
            inbytes.markReaderIndex();
            byte[] comp = new byte[12];
            inbytes.readBytes(comp);
            inbytes.resetReaderIndex();
            return Arrays.equals(this.bytes, comp);
        }
    }
    public interface StunEvents {
        boolean validateUfrag(String ufrag, InetSocketAddress socketAddress); //validates a user fragment can bind
        String getOwnPWD(); //gets server ice pwd
        String getOwnUserfragment(); //gets server userfragment
    }
}
