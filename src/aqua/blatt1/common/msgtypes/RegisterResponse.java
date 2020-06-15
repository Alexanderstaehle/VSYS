package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class RegisterResponse implements Serializable {
    private final String id;
    private long leaseDuration;

    public RegisterResponse(String id, long leaseDuration) {
        this.id = id;
        this.leaseDuration = leaseDuration;
    }

    public String getId() {
        return id;
    }

    public long getLease() {
        return leaseDuration;
    }

}
