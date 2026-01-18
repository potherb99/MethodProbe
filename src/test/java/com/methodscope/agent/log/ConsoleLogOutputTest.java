package com.methodprobe.agent.log;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.*;

/**
 * Unit tests for ConsoleLogOutput.
 */
public class ConsoleLogOutputTest {

    @Test
    public void testWriteToConsole() {
        // Capture System.out
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ConsoleLogOutput logOutput = ConsoleLogOutput.getInstance();

            String testMessage = "Test console message";
            logOutput.write(testMessage);
            logOutput.flush();

            assertEquals(testMessage, outContent.toString());
        } finally {
            // Restore System.out
            System.setOut(originalOut);
        }
    }

    @Test
    public void testSingleton() {
        ConsoleLogOutput instance1 = ConsoleLogOutput.getInstance();
        ConsoleLogOutput instance2 = ConsoleLogOutput.getInstance();

        assertSame("Should return same instance", instance1, instance2);
    }

    @Test
    public void testImplementsLogOutput() {
        ConsoleLogOutput logOutput = ConsoleLogOutput.getInstance();
        assertTrue("Should implement LogOutput", logOutput instanceof LogOutput);
    }
}
