package xyz.breadloaf.voicertc;

import de.maxhenkel.configbuilder.ConfigBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.semver4j.Semver;
import xyz.breadloaf.voicertc.api.RTCEntrypoint;
import xyz.breadloaf.voicertc.config.Config;
import xyz.breadloaf.voicertc.sockets.RTCMultiplexClientSocket;
import xyz.breadloaf.voicertc.sockets.UDPMultiplexParentSocket;
import xyz.breadloaf.voicertc.webrtc.StreamEventRouter;
import xyz.breadloaf.voicertc.webrtc.UserRespectingThreadpool;
import xyz.breadloaf.voicertc.webrtc.WebRTCUser;
import xyz.breadloaf.voicertc.webrtc.ice.IceUtils;
import xyz.breadloaf.voicertc.webrtc.stun.StunUtils;

import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class VoiceRTC implements ModInitializer {
    public static String MODID = "VoiceRTC";
    public static Logger logger = LogManager.getLogger(MODID);
    public static UDPMultiplexParentSocket ParentSocket;
    public static ConcurrentHashMap<InetSocketAddress,WebRTCUser> SocketUserMap = new ConcurrentHashMap<>();
    public static X509Certificate CERTIFICATE;
    public static File CERTIFICATE_FILE;
    public static File KEY_FILE;
    public static ExecutorService WEBRTC_THREAD_POOL = Executors.newFixedThreadPool(4);
    public static UserRespectingThreadpool WEBRTC_THREAD_POOL_RESPECT = new UserRespectingThreadpool(4);
    public static ScheduledExecutorService WEBRTC_THREAD_POOL_SCHEDULE = Executors.newScheduledThreadPool(2);
    public static ExecutorService HANDSHAKE_THREADPOOL = new ThreadPoolExecutor(1,Integer.MAX_VALUE,60L,TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    public static boolean supportsPacketMatching = false;
    public static Config CONFIG;
    public static String VOICE_HOST = "";
    public static RTCMultiplexClientSocket clientSocket;
    public static ArrayList<InetSocketAddress> stunAddresses = new ArrayList<>();
    public static Thread POOL_MONITOR = new Thread(new Runnable() {
        @Override
        public void run() {
            for(;;) {
                AtomicLong WebRTCDelay = new AtomicLong(-1);
                AtomicLong ScheduleDelay = new AtomicLong(-1);
                //AtomicLong HttpDelay = new AtomicLong(-1);
                AtomicLong HandshakeDelay = new AtomicLong(-1);

                long now = System.currentTimeMillis();
                WEBRTC_THREAD_POOL.execute(()->{
                    WebRTCDelay.set(System.currentTimeMillis() - now);
                });
                WEBRTC_THREAD_POOL_SCHEDULE.execute(()->{
                    ScheduleDelay.set(System.currentTimeMillis() - now);
                });
                HANDSHAKE_THREADPOOL.execute(()->{
                    HandshakeDelay.set(System.currentTimeMillis() - now);
                });

                while(true) {
                    if (WebRTCDelay.get() != -1 && ScheduleDelay.get() != -1 && HandshakeDelay.get() != -1) {
                        break;
                    }
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (WebRTCDelay.get() > 100 || ScheduleDelay.get() > 100 || HandshakeDelay.get() > 100) {
                    VoiceRTC.logger.warn("Warning, VoiceRTC threadpool latency was over 100ms, (%d %d %d)".formatted(WebRTCDelay.get(),ScheduleDelay.get(),HandshakeDelay.get()));
                }
            }
        }
    });
    public static boolean stunFailed = false;
    public static Path configPath;
    public static void writeFingerprint(StringBuilder stringBuilder) throws Exception {
        try (FileInputStream is = new FileInputStream(CERTIFICATE_FILE)) {
             CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
             X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(is);
             MessageDigest digest = MessageDigest.getInstance("SHA-256");
             char[] hex = Hex.toHexString(digest.digest(cert.getEncoded())).toCharArray();
             for (int i = 0; i < hex.length; i+=2) {
                 if (i != 0) {
                     stringBuilder.append(":");
                 }
                 stringBuilder.append(Character.toUpperCase(hex[i]));
                 stringBuilder.append(Character.toUpperCase(hex[i+1]));
             }
        }
    }
    public static String formatAsPEM(Certificate certificate) throws CertificateEncodingException {
        String beginCert = "-----BEGIN CERTIFICATE-----";
        String endCert = "-----END CERTIFICATE-----";
        String lineSeparator = System.getProperty("line.separator");
        Base64.Encoder encoder = Base64.getMimeEncoder(64, lineSeparator.getBytes());
        byte[] rawCert = certificate.getEncoded();
        String encodedCert = new String(encoder.encode(rawCert));
        return beginCert + lineSeparator + encodedCert + lineSeparator + endCert;
    }
    public static String formatAsPEM(PrivateKey privateKey) {
        String beginKey = "-----BEGIN PRIVATE KEY-----";
        String endKey = "-----END PRIVATE KEY-----";
        String lineSeparator = System.getProperty("line.separator");
        Base64.Encoder encoder = Base64.getMimeEncoder(64, lineSeparator.getBytes());
        byte[] rawKey = privateKey.getEncoded();
        String encodedKey = new String(encoder.encode(rawKey));
        return beginKey + lineSeparator + encodedKey + lineSeparator + endKey;
    }
    public static X509Certificate selfSign(KeyPair keyPair, String subjectDN) throws OperatorCreationException, CertificateException, IOException, OperatorCreationException {
        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Name dnName = new X500Name(subjectDN);
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1); // <-- 1 Yr validity

        Date endDate = calendar.getTime();

        String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.

        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        // Extensions --------------------------

        // Basic Constraint
        BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity

        certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.

        // -------------------------------------

        return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));
    }
    public void createCertificate(File cert) throws NoSuchAlgorithmException, IOException, CertificateException, OperatorCreationException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        CERTIFICATE = selfSign(kp,"CN=WebRTC");
        BufferedWriter writer = new BufferedWriter(new FileWriter(cert));
        writer.write(formatAsPEM(CERTIFICATE));
        writer.close();
        writer = new BufferedWriter(new FileWriter(configPath.resolve("key.pem").toFile()));
        writer.write(formatAsPEM(kp.getPrivate()));
        writer.close();
    }

    @Override
    public void onInitialize() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("VoiceRTC");
        CONFIG = ConfigBuilder.build(configPath.resolve("voicertc.properties"), Config::new);
        try {
            IceUtils.generateServerCredentials();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        ModContainer voicechatMod = FabricLoader.getInstance().getModContainer("voicechat").orElse(null);

        if (voicechatMod == null) {
            throw new IllegalStateException("voicechat mod not found!");
        }
        String[] voicechatVersion = voicechatMod.getMetadata().getVersion().getFriendlyString().split("-");
        Semver voicechatApiVersion;
        // this is not super important but it makes the mod cleaner, if this fails it will just log more warnings
        // this handles the following cases, this should be like 99.99% fine
        // 1.19.2-2.4.0-pre1
        // 1.19.2-2.4.0
        // 22w46a-2.4.0
        // 1.19.3-pre1-2.4.0
        // 1.19.3-pre1-2.4.0-pre1
        // where 2.4.0, is the voicechat api verison
        if (voicechatVersion[voicechatVersion.length-1].startsWith("pre")) {
            voicechatApiVersion = new Semver(voicechatVersion[voicechatVersion.length-2]);
        } else {
            voicechatApiVersion = new Semver(voicechatVersion[voicechatVersion.length-1]);
        }
        supportsPacketMatching = voicechatApiVersion.isGreaterThanOrEqualTo("2.3.0");
        if (!supportsPacketMatching) {
            logger.warn("Warning, voicechat api version is less then 2.3.0, you will get warnings about invalid packets!");
            logger.warn("Detected voicechat version " + voicechatMod.getMetadata().getVersion().getFriendlyString());
        }

        logger.info("Generating certificate for WebRTC");
        try {
            configPath.toFile().mkdirs();
            File cert = configPath.resolve("cert.pem").toFile();
            CERTIFICATE_FILE = cert;
            KEY_FILE = configPath.resolve("key.pem").toFile();
            //need to regen every time the server starts
            cert.delete();
            KEY_FILE.delete();
            if (cert.createNewFile()) {
                createCertificate(cert);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        logger.info("Done generating certificate");

        POOL_MONITOR.setDaemon(true);
        POOL_MONITOR.start();


    }

    public static void RTCAvailable() {
        if (VoiceRTC.CONFIG.AllowSTUNClient.get()) {
            logger.info("Starting STUN Client process");
            clientSocket.stunUtils.getOwnAddress(inetSocketAddress -> {
                logger.info("STUN Client got ip: "+inetSocketAddress.toString());
                stunAddresses.add(inetSocketAddress);
            });
        }
        List<RTCEntrypoint> entrypoints = FabricLoader.getInstance().getEntrypointContainers("voicertc", RTCEntrypoint.class).stream().map(EntrypointContainer::getEntrypoint).toList();
        logger.info("Registering " + entrypoints.size() + " VoiceRTC Plugins");
        for (RTCEntrypoint entrypoint : entrypoints) {
            try {
                logger.info("Setting up plugin: " + entrypoint.getID());
                entrypoint.initialize(StreamEventRouter.getApi(entrypoint.getID()));
            } catch (Throwable e) {
                logger.error("Error while setting up plugin: "+ entrypoint.getID());
                logger.error(e);
                logger.warn("Skipping plugin " + entrypoint.getID() + " due to errors");
            }
        }
        logger.info("Setup complete!");
    }
}
