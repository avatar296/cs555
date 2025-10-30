/* CS555 Distributed Systems - HW4 */
package csx55.dfs.erasure;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import csx55.dfs.base.BaseController;
import csx55.dfs.protocol.*;
import csx55.dfs.transport.TCPConnection;
import csx55.dfs.util.DFSConfig;

public class Controller extends BaseController {

    private final Map<String, Map<Integer, String>> fragmentLocations;

    public Controller(int port) {
        super(port);
        this.fragmentLocations = new ConcurrentHashMap<>();
    }

    @Override
    protected String getControllerType() {
        return "Erasure Coding Controller";
    }

    @Override
    protected int getReplicationFactor() {
        return DFSConfig.TOTAL_SHARDS;
    }

    @Override
    protected void updateLocationsFromHeartbeat(
            String serverId, List<HeartbeatMessage.ChunkInfo> chunks, MessageType type) {
        if (chunks != null) {
            for (HeartbeatMessage.ChunkInfo chunk : chunks) {
                String key = chunk.filename + ":" + chunk.chunkNumber;
                fragmentLocations
                        .computeIfAbsent(key, k -> new HashMap<>())
                        .put(chunk.fragmentNumber, serverId);
            }
        }
    }

    @Override
    protected Set<String> getLocationKeys() {
        return fragmentLocations.keySet();
    }

    @Override
    protected void handleAdditionalMessages(Message message, TCPConnection connection)
            throws Exception {
        // Erasure mode doesn't have additional message types
        System.err.println("Unknown message type: " + message.getType());
    }

    public List<String> getFragmentLocationsForRead(String filename, int chunkNumber) {
        String key = filename + ":" + chunkNumber;
        Map<Integer, String> fragmentMap = fragmentLocations.get(key);

        if (fragmentMap == null || fragmentMap.isEmpty()) {
            System.err.println("No fragments found for " + filename + " chunk " + chunkNumber);
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>(Collections.nCopies(DFSConfig.TOTAL_SHARDS, null));
        for (Map.Entry<Integer, String> entry : fragmentMap.entrySet()) {
            int fragmentNumber = entry.getKey();
            String serverId = entry.getValue();
            if (fragmentNumber >= 0 && fragmentNumber < DFSConfig.TOTAL_SHARDS) {
                result.set(fragmentNumber, serverId);
            }
        }

        return result;
    }

    @Override
    protected void initiateRecovery(String failedServerId) {
        System.out.println("Initiating recovery for failed server: " + failedServerId);

        List<String> affectedChunks = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, String>> entry : fragmentLocations.entrySet()) {
            String chunkKey = entry.getKey();
            Map<Integer, String> fragmentMap = entry.getValue();

            for (String serverId : fragmentMap.values()) {
                if (serverId.equals(failedServerId)) {
                    affectedChunks.add(chunkKey);
                    break;
                }
            }
        }

        System.out.println("Found " + affectedChunks.size() + " chunks affected by failure");

        for (String chunkKey : affectedChunks) {
            String[] parts = chunkKey.split(":");
            String filename = parts[0];
            int chunkNumber = Integer.parseInt(parts[1]);

            Map<Integer, String> fragmentMap = fragmentLocations.get(chunkKey);

            Set<Integer> missingFragments = new HashSet<>();
            for (int i = 0; i < DFSConfig.TOTAL_SHARDS; i++) {
                if (!fragmentMap.containsKey(i) || fragmentMap.get(i).equals(failedServerId)) {
                    missingFragments.add(i);
                }
            }

            for (int fragmentNumber : missingFragments) {
                if (fragmentMap.containsKey(fragmentNumber)
                        && fragmentMap.get(fragmentNumber).equals(failedServerId)) {
                    fragmentMap.remove(fragmentNumber);
                }
            }

            int availableFragments = DFSConfig.TOTAL_SHARDS - missingFragments.size();

            if (availableFragments < DFSConfig.DATA_SHARDS) {
                System.err.println(
                        "ERROR: Not enough fragments to recover "
                                + chunkKey
                                + " (have "
                                + availableFragments
                                + ", need "
                                + DFSConfig.DATA_SHARDS
                                + ")");
                continue;
            }

            System.out.println(
                    "Recovering " + missingFragments.size() + " missing fragments for " + chunkKey);

            Set<String> sourceServers = new HashSet<>();
            for (Map.Entry<Integer, String> fragEntry : fragmentMap.entrySet()) {
                sourceServers.add(fragEntry.getValue());
            }

            List<String> sourceServerList = new ArrayList<>(sourceServers);

            if (sourceServerList.isEmpty()) {
                System.err.println("ERROR: No source servers available for " + chunkKey);
                continue;
            }

            for (int missingFragmentNumber : missingFragments) {
                String targetServer = selectNewServerForRecovery(fragmentMap.values());

                if (targetServer == null) {
                    System.err.println(
                            "ERROR: Cannot find new server for fragment "
                                    + missingFragmentNumber
                                    + " of "
                                    + chunkKey);
                    continue;
                }

                String coordinatorServer = sourceServerList.get(0);

                try {
                    TCPConnection.Address addr = TCPConnection.Address.parse(coordinatorServer);
                    try (Socket socket = new Socket(addr.host, addr.port);
                            TCPConnection connection = new TCPConnection(socket)) {

                        ReconstructFragmentRequest request =
                                new ReconstructFragmentRequest(
                                        filename,
                                        chunkNumber,
                                        missingFragmentNumber,
                                        sourceServerList,
                                        targetServer);
                        connection.sendMessage(request);

                        Message response = connection.receiveMessage();
                        ReconstructFragmentResponse reconstructResponse =
                                (ReconstructFragmentResponse) response;

                        if (reconstructResponse.isSuccess()) {
                            fragmentMap.put(missingFragmentNumber, targetServer);
                            System.out.println(
                                    "Successfully reconstructed fragment "
                                            + missingFragmentNumber
                                            + " of "
                                            + chunkKey
                                            + " to "
                                            + targetServer);
                        } else {
                            System.err.println(
                                    "Failed to reconstruct fragment "
                                            + missingFragmentNumber
                                            + " of "
                                            + chunkKey
                                            + ": "
                                            + reconstructResponse.getErrorMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println(
                            "Error reconstructing fragment "
                                    + missingFragmentNumber
                                    + " of "
                                    + chunkKey
                                    + ": "
                                    + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java csx55.dfs.erasure.Controller <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            Controller controller = new Controller(port);
            controller.start();
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error starting controller: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
