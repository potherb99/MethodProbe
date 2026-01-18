package com.methodprobe.agent.snapshot;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Command-line tool to read and display snapshot files.
 * Usage: java -cp agent.jar com.methodprobe.agent.snapshot.SnapshotReader
 * <snapshot-file-or-pattern>
 * 
 * Supports wildcard patterns:
 * - *.snapshot (all snapshot files in current directory)
 * - *00031*.snapshot (files containing "00031")
 * - ./probe-snapshots/** (all files in directory recursively)
 */
public class SnapshotReader {

    private static final byte[] MAGIC = new byte[] { 'M', 'T', 'S', 'S' };

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp agent.jar com.methodprobe.agent.snapshot.SnapshotReader <pattern>");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  Single file:       ./probe-snapshots/2026-01-12/xxx.snapshot");
            System.out.println("  Wildcard:          ./probe-snapshots/2026-01-12/*.snapshot");
            System.out.println("  Contains pattern:  ./probe-snapshots/**/*00031*.snapshot");
            System.out.println("  All snapshots:     ./probe-snapshots/**/*.snapshot");
            System.exit(1);
        }

        String pattern = args[0];
        try {
            List<Path> files = findMatchingFiles(pattern);
            if (files.isEmpty()) {
                System.out.println("No matching snapshot files found for pattern: " + pattern);
                System.exit(1);
            }

            Collections.sort(files);
            System.out.println("Found " + files.size() + " snapshot file(s)\n");

            for (Path file : files) {
                try {
                    MethodSnapshot snapshot = readSnapshot(file.toString());
                    printSnapshot(snapshot);
                    System.out.println(); // Blank line between snapshots
                } catch (Exception e) {
                    System.err.println("Failed to read " + file + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to read snapshots: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Find files matching a glob pattern.
     * If the path is a regular file, returns it directly.
     */
    private static List<Path> findMatchingFiles(String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        Path patternPath = Paths.get(pattern);

        // If it's a direct file path (no wildcards), just return it
        if (Files.isRegularFile(patternPath)) {
            result.add(patternPath);
            return result;
        }

        // Handle glob pattern
        // Find the base directory (part before any wildcards)
        String patternStr = pattern.replace("\\", "/");
        int wildcardIdx = patternStr.indexOf('*');

        Path baseDir;
        String globPattern;

        if (wildcardIdx == -1) {
            // No wildcard - maybe it's a directory, list all .snapshot files
            if (Files.isDirectory(patternPath)) {
                baseDir = patternPath;
                globPattern = "glob:**/*.snapshot";
            } else {
                // Not a file, not a directory, no wildcards - nothing matches
                return result;
            }
        } else {
            // Extract base directory and pattern
            String basePath = patternStr.substring(0, wildcardIdx);
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash >= 0) {
                baseDir = Paths.get(basePath.substring(0, lastSlash + 1));
                globPattern = "glob:" + patternStr.substring(lastSlash + 1);
            } else {
                baseDir = Paths.get(".");
                globPattern = "glob:" + patternStr;
            }
        }

        if (!Files.isDirectory(baseDir)) {
            return result;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relativePath = baseDir.relativize(file);
                if (matcher.matches(relativePath) || matcher.matches(file.getFileName())) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    /**
     * Read a snapshot from file.
     */
    public static MethodSnapshot readSnapshot(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {

            // Read and verify magic
            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] ||
                    magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("Invalid snapshot file format");
            }

            // Read version
            int version = dis.readInt();
            if (version != 1) {
                throw new IOException("Unsupported snapshot version: " + version);
            }

            // Read snapshotId
            String snapshotId = dis.readUTF();

            // Read header
            MethodSnapshot snapshot = new MethodSnapshot();
            snapshot.setSnapshotId(snapshotId.isEmpty() ? null : snapshotId);
            snapshot.setTimestamp(dis.readLong());
            snapshot.setClassName(dis.readUTF());
            snapshot.setMethodName(dis.readUTF());
            snapshot.setThreadName(dis.readUTF());
            snapshot.setDurationMs(dis.readDouble());

            // Read arg types
            int argTypeCount = dis.readInt();
            if (argTypeCount > 0) {
                String[] argTypes = new String[argTypeCount];
                for (int i = 0; i < argTypeCount; i++) {
                    argTypes[i] = dis.readUTF();
                }
                snapshot.setArgTypes(argTypes);
            }

            // Read serialized args
            int argCount = dis.readInt();
            if (argCount > 0) {
                byte[][] serializedArgs = new byte[argCount][];
                for (int i = 0; i < argCount; i++) {
                    int len = dis.readInt();
                    if (len >= 0) {
                        serializedArgs[i] = new byte[len];
                        dis.readFully(serializedArgs[i]);
                    }
                }
                snapshot.setSerializedArgs(serializedArgs);
            }

            // Read exception
            int exceptionLen = dis.readInt();
            if (exceptionLen >= 0) {
                byte[] exception = new byte[exceptionLen];
                dis.readFully(exception);
                snapshot.setSerializedException(exception);
            }

            return snapshot;
        }
    }

    /**
     * Print snapshot in human-readable format.
     */
    public static void printSnapshot(MethodSnapshot snapshot) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        System.out.println("╔══════════════════════════════════════════════════════════════");
        System.out.println("║ Method Snapshot");
        System.out.println("╠══════════════════════════════════════════════════════════════");
        if (snapshot.getSnapshotId() != null) {
            System.out.printf("║ ID:       %s%n", snapshot.getSnapshotId());
        }
        System.out.printf("║ Time:     %s%n", sdf.format(new Date(snapshot.getTimestamp())));
        System.out.printf("║ Thread:   %s%n", snapshot.getThreadName());
        System.out.printf("║ Method:   %s%n", snapshot.getFullMethodName());
        System.out.printf("║ Duration: %.2f ms%n", snapshot.getDurationMs());

        // Show exception if present
        byte[] exceptionData = snapshot.getSerializedException();
        if (exceptionData != null && exceptionData.length > 0) {
            Object exObj = SnapshotSerializer.deserialize(exceptionData);
            if (exObj instanceof ExceptionInfo) {
                // New format: ExceptionInfo object
                ExceptionInfo exInfo = (ExceptionInfo) exObj;
                System.out.println("╠══════════════════════════════════════════════════════════════");
                System.out.printf("║ ⚠ Exception: %s%n", exInfo.getExceptionClass());
                if (exInfo.getMessage() != null) {
                    System.out.printf("║   Message: %s%n", exInfo.getMessage());
                }
                // Print stack trace (first 8 lines)
                String stackTrace = exInfo.getStackTrace();
                if (stackTrace != null && !stackTrace.isEmpty()) {
                    System.out.println("║   Stack Trace:");
                    String[] lines = stackTrace.split("\n");
                    int maxLines = Math.min(8, lines.length);
                    for (int i = 0; i < maxLines; i++) {
                        String line = lines[i].trim();
                        if (!line.isEmpty()) {
                            System.out.printf("║     %s%n", line);
                        }
                    }
                    if (lines.length > maxLines) {
                        System.out.printf("║     ... %d more lines%n", lines.length - maxLines);
                    }
                }
            } else if (exObj instanceof Throwable) {
                // Legacy format: Throwable object (for backward compatibility)
                Throwable ex = (Throwable) exObj;
                System.out.println("╠══════════════════════════════════════════════════════════════");
                System.out.printf("║ ⚠ Exception: %s%n", ex.getClass().getName());
                if (ex.getMessage() != null) {
                    System.out.printf("║   Message: %s%n", ex.getMessage());
                }
            }
        }

        System.out.println("╠══════════════════════════════════════════════════════════════");
        System.out.println("║ Arguments:");

        String[] argTypes = snapshot.getArgTypes();
        byte[][] serializedArgs = snapshot.getSerializedArgs();

        if (argTypes == null || argTypes.length == 0) {
            System.out.println("║   (none)");
        } else {
            for (int i = 0; i < argTypes.length; i++) {
                String type = argTypes[i];
                Object value = null;
                if (serializedArgs != null && i < serializedArgs.length && serializedArgs[i] != null) {
                    value = SnapshotSerializer.deserialize(serializedArgs[i]);
                }
                System.out.printf("║   [%d] %s = %s%n", i, type, formatValue(value));
            }
        }

        System.out.println("╚══════════════════════════════════════════════════════════════");
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        String str = value.toString();
        if (str.length() > 200) {
            return str.substring(0, 200) + "...";
        }
        return str;
    }
}
