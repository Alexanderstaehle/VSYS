package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NameResolutionResponse implements Serializable {

    private String requestId;
    private InetSocketAddress tankAddress;

    public NameResolutionResponse(String requestId, InetSocketAddress tankAddress) {
        this.requestId = requestId;
        this.tankAddress = tankAddress;
    }

}
