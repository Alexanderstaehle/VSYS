package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

public class SnapshotCollector implements Serializable {
    public int counter;

    public SnapshotCollector(int localState) {
        this.counter += localState;
    }

    public int getCounter() {
        return this.counter;
    }
}
