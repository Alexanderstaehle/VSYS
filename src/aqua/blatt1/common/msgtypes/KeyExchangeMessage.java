package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.security.PublicKey;

public class KeyExchangeMessage implements Serializable {
    private PublicKey publicKey;

    public KeyExchangeMessage(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
