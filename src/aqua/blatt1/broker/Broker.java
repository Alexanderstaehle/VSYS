package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.SecureEndpoint;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    SecureEndpoint endpoint;
    ClientCollection<InetSocketAddress> clientList;
    Boolean stopRequest;
    ExecutorService executor;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    int idCounter = 0;
    protected Timer timer = new Timer();
    long leaseDuration = 10000;

    public Broker() {
        endpoint = new SecureEndpoint(4711);
        clientList = new ClientCollection<>();
        stopRequest = false;
        executor = Executors.newFixedThreadPool(5);
    }

    private class BrokerTask {
        public void brokerTask(Message msg) {
            if (msg.getPayload() instanceof RegisterRequest) {
                synchronized (clientList) {
                    register(msg);
                }
            }

            if (msg.getPayload() instanceof DeregisterRequest) {
                synchronized (clientList) {
                    deregister(msg);
                }
            }

            if (msg.getPayload() instanceof HandoffRequest) {
                lock.writeLock().lock();
                handoffFish(msg);
                lock.writeLock().unlock();
            }

            if (msg.getPayload() instanceof PoisonPill) {
                System.exit(0);
            }

            if (msg.getPayload() instanceof NameResolutionRequest) {
                determineTankAddress(msg);
            }
        }
    }

    private void determineTankAddress(Message msg) {
        String tankId = ((NameResolutionRequest) msg.getPayload()).getTankId();
        InetSocketAddress tankAddress = clientList.getClient(clientList.indexOf(tankId));
        endpoint.send(msg.getSender(), new NameResolutionResponse(((NameResolutionRequest) msg.getPayload()).getRequestId(), tankAddress));
    }

    public void broker() {
        cleanup();
        executor.execute(() -> {
            JOptionPane.showMessageDialog(null, "Ok um Server zu beenden");
            stopRequest = true;
        });
        while (!stopRequest) {
            Message msg = endpoint.blockingReceive();
            BrokerTask brokerTask = new BrokerTask();
            executor.execute(() -> brokerTask.brokerTask(msg));
        }
    }

    private void cleanup() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                long currentTimestamp = System.currentTimeMillis();
                if (clientList.size() > 0) {
                    for (int i = 0; i < clientList.size(); i++) {
                        long clientTimestamp = clientList.getLease(i);
                        long leasingTime = currentTimestamp - clientTimestamp;
                        if (leasingTime > 10000) {
                            endpoint.send((InetSocketAddress) clientList.getClient(i), new LeasingRunOut());
                        }
                    }
                }
            }
        }, 0, 3000);
    }

    private void handoffFish(Message msg) {
        Direction direction = ((HandoffRequest) msg.getPayload()).getFish().getDirection();
        InetSocketAddress receiverAddress;
        int index = clientList.indexOf(msg.getSender());
        if (direction == Direction.LEFT) {
            receiverAddress = clientList.getLeftNeigbhorOf(index);
        } else {
            receiverAddress = clientList.getRightNeigbhorOf(index);
        }
        endpoint.send(receiverAddress, msg.getPayload());
    }

    private void register(Message msg) {
        String id = "";
        id = "tank" + idCounter;
        idCounter++;

        long leaseStart = System.currentTimeMillis();

        InetSocketAddress newTankAddress = msg.getSender();
        int index = clientList.indexOf(newTankAddress);
        if (index == -1) {
            clientList.add(id, newTankAddress, leaseStart);
        } else {
            clientList.updateLease(index, leaseStart);
        }


        InetSocketAddress leftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(newTankAddress));
        InetSocketAddress rightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(newTankAddress));

        InetSocketAddress leftNeighborOfLeftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(leftNeighbor));
        InetSocketAddress rightNeighborOfRightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(rightNeighbor));


        if (clientList.size() == 1) {
            endpoint.send(newTankAddress, new Token());
            endpoint.send(newTankAddress, new NeighborUpdate(newTankAddress, newTankAddress));
        } else {
            endpoint.send(leftNeighbor, new NeighborUpdate(leftNeighborOfLeftNeighbor, newTankAddress));
            endpoint.send(rightNeighbor, new NeighborUpdate(newTankAddress, rightNeighborOfRightNeighbor));
            endpoint.send(newTankAddress, new NeighborUpdate(leftNeighbor, rightNeighbor));
        }
        endpoint.send(newTankAddress, new RegisterResponse(id, leaseDuration));
    }

    private void deregister(Message msg) {
        InetSocketAddress deletableClient = msg.getSender();
        InetSocketAddress leftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(deletableClient));
        InetSocketAddress rightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(deletableClient));

        InetSocketAddress leftNeighborOfLeftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(leftNeighbor));
        InetSocketAddress rightNeighborOfRightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(rightNeighbor));
        if (clientList.size() == 2) {
            endpoint.send(leftNeighbor, new NeighborUpdate(leftNeighbor, leftNeighbor));
        } else {
            endpoint.send(leftNeighbor, new NeighborUpdate(leftNeighborOfLeftNeighbor, rightNeighbor));
            endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, rightNeighborOfRightNeighbor));
        }

        clientList.remove(clientList.indexOf(((DeregisterRequest) msg.getPayload()).getId()));
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }
}
