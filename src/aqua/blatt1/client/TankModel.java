package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordState;
import aqua.blatt1.common.msgtypes.*;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;
    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;
    protected Boolean hasToken;
    protected Timer timer = new Timer();
    public RecordState recordState = RecordState.IDLE;
    public int localState;
    public boolean initiatedSnapshot;
    public boolean hasCollector;
    public ExecutorService executor = Executors.newFixedThreadPool(5);
    public int globalState;
    public boolean globalStateReady;
    protected final HashMap homeAgent;


    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
        this.hasToken = false;
        this.homeAgent = new HashMap();
    }

    synchronized void onRegistration(String id, long lease) {
        this.id = id;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                forwarder.register();
            }
        }, lease-1000);
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
            homeAgent.put(fish.getId(), null);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        System.out.println(recordState);
        fish.setToStart();
        fishies.add(fish);
        if (homeAgent.containsKey(fish.getId())) {
            homeAgent.replace(fish.getId(), null);
        } else {
            forwarder.sendNameResolutionRequest(fish.getTankId(), fish.getId());
        }
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge())
                hasToken(fish);

            if (fish.disappears())
                it.remove();
        }
    }

    private void hasToken(FishModel fish) {
        if (hasToken) {
            forwarder.handOff(fish, leftNeighbor, rightNeighbor);
        } else {
            fish.reverse();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
            executor.shutdown();
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public void updateNeighbors(InetSocketAddress addressLeft, InetSocketAddress addressRight) {
        this.leftNeighbor = addressLeft;
        this.rightNeighbor = addressRight;
    }

    public void receiveToken(Token token) {
        hasToken = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                hasToken = false;
                forwarder.sendToken(leftNeighbor, token);
            }
        }, 2000);
    }

    public void initiateSnapshot() {
        if (recordState == RecordState.IDLE) {
            localState = fishies.size();
            initiatedSnapshot = true;
            recordState = RecordState.BOTH;

            forwarder.sendSnapshotMarker(leftNeighbor, new SnapshotMarker());
            forwarder.sendSnapshotMarker(rightNeighbor, new SnapshotMarker());
        }
    }

    public void receiveSnapshotMarker(InetSocketAddress sender, SnapshotMarker snapshotMarker) {
        if (recordState == RecordState.IDLE) {
            // lokaler snapshot
            localState = fishies.size();

            // Starte weitere Aufzeichnungskanäle

            if (leftNeighbor.equals(rightNeighbor)) { // 2 Tanks
                recordState = RecordState.RIGHT;
                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
            } else {

                if (sender.equals(leftNeighbor)) {
                    recordState = RecordState.RIGHT;
                } else if (sender.equals(rightNeighbor)) {
                    recordState = RecordState.LEFT;
                }


                forwarder.sendSnapshotMarker(leftNeighbor, snapshotMarker);
                forwarder.sendSnapshotMarker(rightNeighbor, snapshotMarker);
            }
        } else {
            if (leftNeighbor.equals(rightNeighbor)) { // 2 Tanks
                recordState = RecordState.IDLE;
            } else {
                if (sender.equals(leftNeighbor)) {
                    if (recordState == RecordState.BOTH) {
                        recordState = RecordState.RIGHT;
                    }

                    if (recordState == RecordState.LEFT)
                        recordState = RecordState.IDLE;
                } else if (sender.equals(rightNeighbor)) {
                    if (recordState == RecordState.BOTH)
                        recordState = RecordState.LEFT;

                    if (recordState == RecordState.RIGHT)
                        recordState = RecordState.IDLE;
                }
            }
        }
        if (recordState == RecordState.IDLE && initiatedSnapshot) {
            forwarder.sendSnapshotCollector(leftNeighbor, new SnapshotCollector(localState));
        }
        System.out.println("Local State: " + localState);
    }

    public void receiveSnapshotCollector(SnapshotCollector snapshotCollector) {
        hasCollector = true;
        executor.execute(() -> {
            while (hasCollector) {
                if (recordState == RecordState.IDLE && !initiatedSnapshot) {
                    int counter = snapshotCollector.getCounter() + localState;
                    forwarder.sendSnapshotCollector(leftNeighbor, new SnapshotCollector(counter));
                    hasCollector = false;
                }
            }
        });

        if (initiatedSnapshot) {
            initiatedSnapshot = false;
            globalState = snapshotCollector.getCounter();
            System.out.println("Global State: " + globalState);
            globalStateReady = true;
        }
    }

    public void locateFishGlobally(String fishId) {
        if (homeAgent.get(fishId) == null) {
            locateFishLocally(fishId);
        } else {
            forwarder.sendLocationRequest((InetSocketAddress) homeAgent.get(fishId), fishId);
        }

    }

    public void locateFishLocally(String fishId) {
        for (FishModel fish : this) {
            if (fish.getId().equals(fishId)) {
                fish.toggle();
                break;
            }
        }
    }

    public void sendLocationUpdate(InetSocketAddress tankAddress, String fishId) {
        forwarder.sendLocationUpdate(tankAddress, fishId);
    }

    public void handleLocationUpdate(String fishId, InetSocketAddress sender) {
        homeAgent.replace(fishId, sender);
    }

    public void handleLeasingRunOut() {
        forwarder.deregister(id);
        System.exit(0);
    }
}