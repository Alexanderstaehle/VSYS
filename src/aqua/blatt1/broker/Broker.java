package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    Endpoint endpoint;
    ClientCollection<InetSocketAddress> clientList;
    Boolean stopRequest;
    ExecutorService executor;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    int idCounter = 0;

    public Broker() {
        endpoint = new Endpoint(4711);
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
        }
    }

    public void broker() {
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

        InetSocketAddress newTankAddress = msg.getSender();

        clientList.add(id, newTankAddress);


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
        endpoint.send(newTankAddress, new RegisterResponse(id));
    }

    private void deregister(Message msg) {
        InetSocketAddress deletableClient = msg.getSender();
        InetSocketAddress leftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(deletableClient));
        InetSocketAddress rightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(deletableClient));

        InetSocketAddress leftNeighborOfLeftNeighbor = clientList.getLeftNeigbhorOf(clientList.indexOf(leftNeighbor));
        InetSocketAddress rightNeighborOfRightNeighbor = clientList.getRightNeigbhorOf(clientList.indexOf(rightNeighbor));

        endpoint.send(leftNeighbor, new NeighborUpdate(leftNeighborOfLeftNeighbor, rightNeighbor));
        endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, rightNeighborOfRightNeighbor));

        clientList.remove(clientList.indexOf(((DeregisterRequest) msg.getPayload()).getId()));
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }
}
