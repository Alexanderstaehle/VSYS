package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class NameResolutionRequest implements Serializable {

    private String tankId;
    private String requestId;

    public String getTankId() {
        return tankId;
    }

    public String getRequestId() {
        return requestId;
    }


    NameResolutionRequest(String tankId, String requestId){
        this.tankId = tankId;
        this.requestId = requestId;
    }
}
