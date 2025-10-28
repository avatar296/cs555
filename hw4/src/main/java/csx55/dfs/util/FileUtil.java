package csx55.dfs.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility class for file operations
 */
public class FileUtil {

    /**
     * Calculate the size of a directory recursively
     */
    public static long getDirectorySize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0;
        }

        final long[] size = {0};

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Skip files that can't be accessed
                return FileVisitResult.CONTINUE;
            }
        });

        return size[0];
    }

    /**
     * Normalize a path by removing leading "./" and ensuring it starts with "/"
     */
    public static String normalizePath(String path) {
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    /**
     * Extract filename from a path
     */
    public static String getFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * Get parent directory from a path
     */
    public static String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            return path.substring(0, lastSlash);
        }
        return "/";
    }

    /**
     * Format bytes as human-readable string
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
