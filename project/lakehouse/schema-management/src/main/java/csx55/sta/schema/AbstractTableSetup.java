package csx55.sta.schema;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTableSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final SparkSession spark;

  protected AbstractTableSetup(StreamConfig config) {
    this.spark = IcebergSessionBuilder.createSession(getClass().getSimpleName(), config);
  }

  protected abstract String getLayerName();

  protected abstract String getDDLResourcePath();

  protected abstract void createNamespaces();

  protected void performAdditionalSetup() {}

  public final void run() {
    logger.info("Starting {} table setup", getLayerName());

    createNamespaces();

    List<String> ddlFiles = listDDLFiles(getDDLResourcePath());

    int successCount = 0;
    for (String ddlFile : ddlFiles) {
      try {
        String ddl = loadDDLFromResources(ddlFile);
        spark.sql(ddl);
        successCount++;
      } catch (Exception e) {
        logger.error("Failed to process DDL file: {}", ddlFile, e);
        throw new RuntimeException("Failed to execute DDL file: " + ddlFile, e);
      }
    }

    performAdditionalSetup();

    logger.info("{} setup complete: {} tables created", getLayerName(), successCount);
    spark.stop();
  }

  protected void ensureNamespaceExists(String namespace) {
    try {
      spark.sql("CREATE NAMESPACE IF NOT EXISTS " + namespace);
      logger.info("Ensured namespace exists: {}", namespace);
    } catch (Exception e) {
      // Expected: namespace may already exist, but log for visibility
      logger.warn("Failed to ensure namespace {} exists: {}", namespace, e.getMessage());
    }
  }

  private List<String> listDDLFiles(String resourcePath) {
    try {
      ClassLoader classLoader = getClass().getClassLoader();
      URI uri = classLoader.getResource(resourcePath).toURI();

      List<String> ddlFiles = new ArrayList<>();

      if (uri.getScheme().equals("jar")) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
          Path resourceDir = fileSystem.getPath("/" + resourcePath);
          ddlFiles = listSqlFilesFromPath(resourceDir, resourcePath);
        }
      } else {
        Path resourceDir = Paths.get(uri);
        ddlFiles = listSqlFilesFromPath(resourceDir, resourcePath);
      }

      Collections.sort(ddlFiles);
      return ddlFiles;

    } catch (IOException | URISyntaxException e) {
      logger.error("Failed to list DDL files from resource path: {}", resourcePath, e);
      return Collections.emptyList();
    }
  }

  private List<String> listSqlFilesFromPath(Path dir, String resourcePath) throws IOException {
    try (Stream<Path> paths = Files.walk(dir, 1)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".sql"))
          .map(p -> resourcePath + "/" + p.getFileName().toString())
          .collect(Collectors.toList());
    }
  }

  private String loadDDLFromResources(String resourcePath) {
    try {
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

      if (inputStream == null) {
        throw new RuntimeException("DDL file not found in resources: " + resourcePath);
      }

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String ddl = reader.lines().collect(Collectors.joining("\n"));

        if (ddl.trim().isEmpty()) {
          throw new RuntimeException("DDL file is empty: " + resourcePath);
        }

        return ddl;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load DDL file: " + resourcePath, e);
    }
  }

  protected SparkSession getSparkSession() {
    return spark;
  }
}
