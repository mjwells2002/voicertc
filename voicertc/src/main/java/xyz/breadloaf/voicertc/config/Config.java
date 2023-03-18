package xyz.breadloaf.voicertc.config;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.configbuilder.ConfigEntry;

public class Config {
    public ConfigEntry<String> CustomRTCAddress;
    public ConfigEntry<String> StunServer;
    public ConfigEntry<Boolean> AllowLocalInterfaces;
    public ConfigEntry<Boolean> AllowSTUNClient;
    public ConfigEntry<Boolean> AllowVoiceHost;
    public ConfigEntry<Boolean> AllowIPV6Candidates;

    public ConfigEntry<Boolean> AllowIPV4Candidates;
    public ConfigEntry<Boolean> AllowLocalhostCandidates;

    public Config(ConfigBuilder builder) {
        builder.header("VoiceRTC Config File", "Most issues can be solved by just setting CustomAddress to the public ip of the server (with the voicechat port, if it not 1:1 mapping)");

        CustomRTCAddress = builder.stringEntry("CustomAddress", "", "If set, this custom ip (or domain) will be added onto the candidate list, Format is expected to be ip:port or [ip]:port for ipv6, Default: Empty");
        StunServer = builder.stringEntry("StunServer","stun.l.google.com:19302","The STUN server used to determine the public ip of the server, the default is googles stun server, optionally multiple stun servers can be set with a semicolon (;) as the separator, Default: stun.l.google.com:19302");
        AllowLocalInterfaces = builder.booleanEntry("AllowLocalInterfaces",true, "Allows local network interfaces to be used in candidate gathering, Default: True");
        AllowSTUNClient = builder.booleanEntry("AllowSTUNClient",true,"Allows the use of a STUN server to determine public ip automatically, When enabled one request will be sent to the stun server(s) on game start, Default: True");
        AllowVoiceHost = builder.booleanEntry("AllowVoiceHost", true, "Allows the use of the voice_host voicechat config option as an ice candidate, Default: True");
        AllowIPV6Candidates = builder.booleanEntry("AllowIPV6Candidates",true,"When true ipv6 candidates will be generated in the SDP answer, Default: True");
        AllowIPV4Candidates = builder.booleanEntry("AllowIPV4Candidates",true,"When true ipv4 candidates will be generated in the SDP answer, Default: True");
        AllowLocalhostCandidates = builder.booleanEntry("AllowLocalhostCandidates", false, "Allows the use of localhost addresses in candidate generation, this should not be needed in any environment, Default: False");
    }
}