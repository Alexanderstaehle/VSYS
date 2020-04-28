package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    Endpoint endpoint;
    ClientCollection clientList;
    Boolean stopRequest;
    ExecutorService executor;
    ReadWriteLock lock = new ReentrantReadWriteLock();

    public Broker() {
        endpoint = new Endpoint(4711);
        clientList = new ClientCollection();
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
                lock.writeLock();
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
            receiverAddress = (InetSocketAddress) clientList.getLeftNeigbhorOf(index);
        } else {
            receiverAddress = (InetSocketAddress) clientList.getRightNeigbhorOf(index);
        }
        endpoint.send(receiverAddress, msg.getPayload());
    }

    private void deregister(Message msg) {
        clientList.remove(clientList.indexOf(((DeregisterRequest) msg.getPayload()).getId()));
    }

    private void register(Message msg) {
        String id = "";
        id = "tank" + UUID.randomUUID().toString();
        clientList.add(id, msg.getSender());
        endpoint.send(msg.getSender(), new RegisterResponse(id));
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }
}
