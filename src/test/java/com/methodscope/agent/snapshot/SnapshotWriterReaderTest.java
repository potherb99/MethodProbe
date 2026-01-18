package com.methodprobe.agent.snapshot;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Tests for SnapshotWriter and SnapshotReader.
 */
public class SnapshotWriterReaderTest {

    private static final String TEST_DIR = "./target/test-snapshots";

    @BeforeClass
    public static void initWriter() {
        SnapshotWriter.init(TEST_DIR);
    }

    @Before
    public void setUp() throws Exception {
        // Clean up test directory before each test
        cleanup();
        // Ensure directory exists
        Files.createDirectories(Paths.get(TEST_DIR));
    }

    @After
    public void tearDown() throws Exception {
        cleanup();
    }

    private void cleanup() throws IOException {
        Path path = Paths.get(TEST_DIR);
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testWriteAndReadSnapshot() throws Exception {
        // Create a snapshot
        String snapshotId = SnapshotIdGenerator.generate();
        MethodSnapshot original = new MethodSnapshot(
                snapshotId,
                System.currentTimeMillis(),
                "com.example.Service",
                "processData",
                "main",
                123.45);

        original.setArgTypes(new String[] { "java.lang.String", "int" });
        original.setSerializedArgs(new byte[][] {
                SnapshotSerializer.serialize("hello"),
                SnapshotSerializer.serialize(42)
        });

        // Submit and wait for write
        SnapshotWriter.submitSerialized(original);
        Thread.sleep(500); // Wait for async write

        // Find the snapshot file
        Path dateDir = Files.list(Paths.get(TEST_DIR))
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No date directory found"));

        Path snapshotFile = Files.list(dateDir)
                .filter(p -> p.toString().endsWith(".snapshot"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No snapshot file found"));

        // Read it back
        MethodSnapshot loaded = SnapshotReader.readSnapshot(snapshotFile.toString());

        // Verify
        assertEquals(snapshotId, loaded.getSnapshotId());
        assertEquals(original.getClassName(), loaded.getClassName());
        assertEquals(original.getMethodName(), loaded.getMethodName());
        assertEquals(original.getThreadName(), loaded.getThreadName());
        assertEquals(original.getDurationMs(), loaded.getDurationMs(), 0.001);

        // Verify arg types
        assertArrayEquals(original.getArgTypes(), loaded.getArgTypes());

        // Verify deserialized args
        Object arg0 = SnapshotSerializer.deserialize(loaded.getSerializedArgs()[0]);
        Object arg1 = SnapshotSerializer.deserialize(loaded.getSerializedArgs()[1]);
        assertEquals("hello", arg0);
        assertEquals(42, arg1);
    }

    @Test
    public void testSnapshotFilenameUsesId() throws Exception {
        String snapshotId = "20260112-101010-001-00099";
        MethodSnapshot snapshot = new MethodSnapshot(
                snapshotId,
                System.currentTimeMillis(),
                "com.test.Class",
                "method",
                "thread",
                10.0);
        snapshot.setArgTypes(new String[0]);
        snapshot.setSerializedArgs(new byte[0][]);

        SnapshotWriter.submitSerialized(snapshot);
        Thread.sleep(500);

        // Find the file
        Path dateDir = Files.list(Paths.get(TEST_DIR))
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No date directory found"));

        Path snapshotFile = Files.list(dateDir)
                .filter(p -> p.getFileName().toString().equals(snapshotId + ".snapshot"))
                .findFirst()
                .orElse(null);

        assertNotNull("Snapshot file should be named with ID", snapshotFile);
    }

    @Test
    public void testMultipleSnapshots() throws Exception {
        int count = 5;
        for (int i = 0; i < count; i++) {
            MethodSnapshot snapshot = new MethodSnapshot(
                    SnapshotIdGenerator.generate(),
                    System.currentTimeMillis(),
                    "com.test.Multi",
                    "method" + i,
                    "thread",
                    i * 10.0);
            snapshot.setArgTypes(new String[0]);
            snapshot.setSerializedArgs(new byte[0][]);
            SnapshotWriter.submitSerialized(snapshot);
        }

        Thread.sleep(1000);

        // Count files
        Path dateDir = Files.list(Paths.get(TEST_DIR))
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No date directory found"));

        long fileCount = Files.list(dateDir)
                .filter(p -> p.toString().endsWith(".snapshot"))
                .count();

        assertEquals("Should have created all snapshot files", count, fileCount);
    }
}
