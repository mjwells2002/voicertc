package xyz.breadloaf.voicertc.api.signalling;


import xyz.breadloaf.voicertc.api.enums.SignallingState;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface SignallingAPI {
    SignallingClient getClient(Function<String,String> offerProvider);
    interface SignallingClient {
        //expected to be not null by the SIGNALLING_COMPLETE event
        @Nullable
        String getConnectionURL();
        @Nullable String getAnswer();
        @Nullable String getOffer();
        void setOnStateChange(Consumer<SignallingState> onStateChange);
        void start();
    }

}
