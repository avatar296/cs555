package csx55.sta.schema;

import csx55.sta.streaming.config.StreamConfig;
import csx55.sta.streaming.factory.IcebergSessionBuilder;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Abstract base class for Iceberg table setup with SQL DDL auto-discovery.
 *
 * <p><b>DESIGN PATTERN - TEMPLATE METHOD:</b></p>
 * <pre>
 * This class implements the Template Method pattern (Gang of Four):
 * - Defines the skeleton of the table setup algorithm in run()
 * - Delegates specific steps to subclasses via abstract methods
 * - Eliminates code duplication across setup classes
 * - Used by: Flyway, Liquibase, Apache Airflow, Netflix Conductor
 * </pre>
 *
 * <p><b>INDUSTRY BEST PRACTICE - FLYWAY-STYLE MIGRATIONS:</b></p>
 * <pre>
 * Similar to database migration tools (Flyway, Liquibase), this approach:
 * - Scans a conventional directory for SQL files
 * - Executes each DDL file (CREATE TABLE IF NOT EXISTS)
 * - Requires zero boilerplate - just drop SQL file in folder
 * - Used by Netflix, Apple, Adobe for Iceberg schema management
 * </pre>
 *
 * <p><b>HOW TO EXTEND:</b></p>
 * <pre>
 * To create a new table setup class (e.g., GoldTableSetup):
 *
 * 1. Extend this class
 * 2. Implement 3 abstract methods:
 *    - getLayerName(): Return display name (e.g., "Gold Layer")
 *    - getDDLResourcePath(): Return resource path (e.g., "ddl/gold")
 *    - createNamespaces(): Create required Iceberg namespaces
 * 3. Optionally override performAdditionalSetup() for custom logic
 *
 * That's it! ~30-40 lines of code per new layer.
 * </pre>
 *
 * <p><b>WHAT THIS CLASS DOES:</b></p>
 * <ol>
 *   <li>Creates Iceberg namespaces (delegated to subclass)</li>
 *   <li>Auto-discovers all .sql files in resource folder</li>
 *   <li>Executes each DDL file to create tables</li>
 *   <li>Performs additional setup if needed (delegated to subclass)</li>
 *   <li>Logs comprehensive progress and results</li>
 * </ol>
 */
public abstract class AbstractTableSetup {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SparkSession spark;

    protected AbstractTableSetup(StreamConfig config) {
        this.spark = IcebergSessionBuilder.createSession(getClass().getSimpleName(), config);
    }

    /**
     * Get the display name for this layer (used in logging).
     *
     * @return Layer name (e.g., "Silver Layer", "Monitoring Infrastructure")
     */
    protected abstract String getLayerName();

    /**
     * Get the resource path to scan for DDL files.
     *
     * @return Resource path (e.g., "ddl/silver", "ddl/monitoring")
     */
    protected abstract String getDDLResourcePath();

    /**
     * Create all required Iceberg namespaces for this layer.
     *
     * <p><b>IMPLEMENTATION EXAMPLE:</b></p>
     * <pre>
     * protected void createNamespaces() {
     *     ensureNamespaceExists("lakehouse.silver");
     *     ensureNamespaceExists("lakehouse.quarantine");
     * }
     * </pre>
     */
    protected abstract void createNamespaces();

    /**
     * Perform additional setup after DDL execution (optional hook method).
     *
     * <p>Override this method if you need to perform additional setup
     * after all DDL files have been executed. For example, documenting
     * on-demand tables or creating views.</p>
     *
     * <p>Default implementation does nothing.</p>
     */
    protected void performAdditionalSetup() {
        // Hook method - default implementation does nothing
        // Subclasses can override to add custom logic
    }

    /**
     * Execute the table setup process (Template Method).
     *
     * <p><b>ALGORITHM SKELETON:</b></p>
     * <ol>
     *   <li>Create namespaces (delegated to subclass)</li>
     *   <li>Discover all DDL files in resource folder</li>
     *   <li>Execute each DDL file to create tables</li>
     *   <li>Perform additional setup (delegated to subclass)</li>
     *   <li>Log results and stop Spark session</li>
     * </ol>
     *
     * <p><b>TEMPLATE METHOD PATTERN:</b> This method is final and cannot
     * be overridden. Subclasses customize behavior via abstract methods.</p>
     */
    public final void run() {
        logger.info("========================================");
        logger.info("{} Table Setup (Auto-Discovery)", getLayerName());
        logger.info("========================================");

        // Step 1: Create namespaces (delegated to subclass)
        createNamespaces();

        // Step 2: Discover all DDL files in resource folder
        List<String> ddlFiles = listDDLFiles(getDDLResourcePath());
        logger.info("Discovered {} DDL files in {}", ddlFiles.size(), getDDLResourcePath());

        if (ddlFiles.isEmpty()) {
            logger.warn("No DDL files found in {}. No tables to create.", getDDLResourcePath());
        }

        // Step 3: Execute each DDL file
        int successCount = 0;
        for (String ddlFile : ddlFiles) {
            try {
                String ddl = loadDDLFromResources(ddlFile);
                logger.info("");
                logger.info("Executing DDL: {}", ddlFile);
                spark.sql(ddl);
                logger.info("  ✓ Successfully processed: {}", ddlFile);
                successCount++;
            } catch (Exception e) {
                logger.error("  ✗ Failed to process DDL file: {}", ddlFile, e);
                throw new RuntimeException("Failed to execute DDL file: " + ddlFile, e);
            }
        }

        // Step 4: Perform additional setup (hook method)
        performAdditionalSetup();

        // Step 5: Log results
        logger.info("");
        logger.info("========================================");
        logger.info("{} setup complete!", getLayerName());
        logger.info("  Processed: {} DDL files", successCount);
        logger.info("========================================");

        spark.stop();
    }

    /**
     * Helper method to create an Iceberg namespace if it doesn't exist.
     *
     * @param namespace Fully qualified namespace (e.g., "lakehouse.silver")
     */
    protected void ensureNamespaceExists(String namespace) {
        try {
            spark.sql("CREATE NAMESPACE IF NOT EXISTS " + namespace);
            logger.info("✓ Ensured namespace exists: {}", namespace);
        } catch (Exception e) {
            logger.warn("Could not create namespace (may already exist): {}", e.getMessage());
        }
    }

    /**
     * List all .sql files in a resource directory.
     *
     * <p><b>TEACHING NOTE - CLASSPATH RESOURCE SCANNING:</b></p>
     * <p>This method handles two scenarios:</p>
     * <ul>
     *   <li>Development: Resources in IDE (file system)</li>
     *   <li>Production: Resources in JAR (zip filesystem)</li>
     * </ul>
     *
     * @param resourcePath Resource path to scan (e.g., "ddl/silver")
     * @return Sorted list of resource paths to .sql files
     */
    private List<String> listDDLFiles(String resourcePath) {
        try {
            // Get the resource URL
            ClassLoader classLoader = getClass().getClassLoader();
            URI uri = classLoader.getResource(resourcePath).toURI();

            List<String> ddlFiles = new ArrayList<>();

            // Handle both filesystem and JAR resources
            if (uri.getScheme().equals("jar")) {
                // Running from JAR - use filesystem API
                try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    Path resourceDir = fileSystem.getPath("/" + resourcePath);
                    ddlFiles = listSqlFilesFromPath(resourceDir, resourcePath);
                }
            } else {
                // Running from IDE/filesystem
                Path resourceDir = Paths.get(uri);
                ddlFiles = listSqlFilesFromPath(resourceDir, resourcePath);
            }

            // Sort alphabetically for consistent execution order
            Collections.sort(ddlFiles);
            return ddlFiles;

        } catch (IOException | URISyntaxException e) {
            logger.error("Failed to list DDL files from resource path: {}", resourcePath, e);
            return Collections.emptyList();
        }
    }

    /**
     * Helper method to list .sql files from a Path.
     *
     * @param dir          Directory to scan
     * @param resourcePath Base resource path for constructing full paths
     * @return List of resource paths to .sql files
     * @throws IOException if directory cannot be read
     */
    private List<String> listSqlFilesFromPath(Path dir, String resourcePath) throws IOException {
        try (Stream<Path> paths = Files.walk(dir, 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".sql"))
                    .map(p -> resourcePath + "/" + p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Load SQL DDL content from resources.
     *
     * <p><b>TEACHING NOTE:</b> This loads DDL files packaged in the JAR.
     * In production, the JAR contains all resources, so we read from classpath.</p>
     *
     * @param resourcePath Path to DDL file (e.g., "ddl/silver/trips_cleaned.sql")
     * @return SQL DDL content as a String
     */
    private String loadDDLFromResources(String resourcePath) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

            if (inputStream == null) {
                throw new RuntimeException("DDL file not found in resources: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
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

    /**
     * Get the SparkSession for subclasses that need direct access.
     *
     * @return The SparkSession instance
     */
    protected SparkSession getSparkSession() {
        return spark;
    }
}
