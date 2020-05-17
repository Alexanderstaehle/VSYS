package aqua.blatt1.common.msgtypes;

import aqua.blatt1.common.FishModel;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class NeighborUpdate implements Serializable {
    private InetSocketAddress addressRight;
    private InetSocketAddress addressLeft;

    public NeighborUpdate(InetSocketAddress addressLeft, InetSocketAddress addressRight) {
        this.addressRight = addressRight;
        this.addressLeft = addressLeft;
    }

    public InetSocketAddress getAddressLeft() {
        return addressLeft;
    }

    public InetSocketAddress getAddressRight() {
        return addressRight;
    }
}
