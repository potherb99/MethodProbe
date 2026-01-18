package com.methodprobe.agent.log;

import com.methodprobe.agent.config.AgentConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Unit tests for LogOutputFactory.
 */
public class LogOutputFactoryTest {

    @Before
    public void setUp() throws Exception {
        // Reset the singleton instance
        resetFactory();
    }

    @After
    public void tearDown() throws Exception {
        // Clean up
        LogOutputFactory.shutdown();
        resetFactory();
    }

    @Test
    public void testGetConsoleOutput() throws Exception {
        // Set output mode to console
        AgentConfig.setOutputMode("console");

        LogOutput output = LogOutputFactory.getInstance();

        assertTrue("Should be ConsoleLogOutput", output instanceof ConsoleLogOutput);
    }

    @Test
    public void testGetFileOutput() throws Exception {
        // Set output mode to file
        AgentConfig.setOutputMode("file");
        AgentConfig.setOutputDir("./target/test-logs");

        LogOutput output = LogOutputFactory.getInstance();

        assertTrue("Should be AsyncFileLogOutput", output instanceof AsyncFileLogOutput);

        // Clean up
        output.shutdown();
        deleteDirectory(new File("./target/test-logs"));
    }

    @Test
    public void testInit() throws Exception {
        AgentConfig.setOutputMode("console");

        // Init should not throw
        LogOutputFactory.init();

        assertNotNull(LogOutputFactory.getInstance());
    }

    @Test
    public void testWrite() throws Exception {
        AgentConfig.setOutputMode("console");
        LogOutputFactory.init();

        // Should not throw
        LogOutputFactory.write("Test message\n");
    }

    @Test
    public void testFlush() throws Exception {
        AgentConfig.setOutputMode("console");
        LogOutputFactory.init();

        // Should not throw
        LogOutputFactory.flush();
    }

    private void resetFactory() throws Exception {
        Field instanceField = LogOutputFactory.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        LogOutput oldInstance = (LogOutput) instanceField.get(null);
        if (oldInstance != null) {
            oldInstance.shutdown();
        }
        instanceField.set(null, null);
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
