package csx55.dfs.util;

public class PathUtil {

    public static String normalize(String path) {
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
}
