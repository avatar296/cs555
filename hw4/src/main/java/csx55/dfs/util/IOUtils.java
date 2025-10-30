package csx55.dfs.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IOUtils {

    private IOUtils() {}

    public static void writeWithDirectoryCreation(Path path, byte[] data) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }

    public static byte[] readWithExistenceCheck(Path path, String errorMessage) throws IOException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException(errorMessage);
        }
        return Files.readAllBytes(path);
    }
}
