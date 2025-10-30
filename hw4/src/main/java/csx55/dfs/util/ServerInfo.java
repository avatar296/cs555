/* CS555 Distributed Systems - HW4 */
package csx55.dfs.util;

public class ServerInfo {
    public String serverId;
    public long freeSpace;
    public int count;

    public ServerInfo(String serverId) {
        this.serverId = serverId;
        this.freeSpace = 1024 * 1024 * 1024; // 1GB
        this.count = 0;
    }
}
