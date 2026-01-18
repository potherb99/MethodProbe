package com.methodprobe.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import com.methodprobe.agent.config.AgentConfig;
import com.methodprobe.agent.http.HttpConfigServer;
import com.methodprobe.agent.log.LogOutputFactory;
import com.methodprobe.agent.tree.AsyncTreePrinter;

/**
 * JVM Agent entry point for method probe instrumentation.
 * Uses ByteBuddy Advice mechanism for direct bytecode enhancement.
 */
public class MethodProbeAgent {

    private static Instrumentation instrumentation;

    /**
     * Agent premain entry point - called before main method.
     * 
     * @param agentArgs agent arguments (config file path)
     * @param inst      instrumentation instance
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        System.out.println("[MethodProbe] Agent starting...");

        // Load configuration
        AgentConfig.init(agentArgs);

        // Start HTTP server for dynamic configuration
        HttpConfigServer.start(AgentConfig.getHttpPort());

        // Install ByteBuddy agent builder
        installAgent(inst);

        // Initialize log output (console or async file)
        LogOutputFactory.init();

        // Initialize async tree printer
        AsyncTreePrinter.init();

        // Initialize snapshot writer if enabled
        if (AgentConfig.snapshotEnabled) {
            com.methodprobe.agent.snapshot.SnapshotWriter.init(AgentConfig.snapshotDir);
        }

        // Register shutdown hook to flush logs
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MethodProbe] Shutting down...");
            com.methodprobe.agent.snapshot.SnapshotWriter.shutdown();
            AsyncTreePrinter.shutdown();
            LogOutputFactory.shutdown();
            System.out.println("[MethodProbe] Shutdown complete.");
        }, "Methodprobe-Shutdown"));

        // Start stats reporter
        StatsReporter.start();

        System.out.println("[MethodProbe] Agent started successfully.");
    }

    /**
     * Install ByteBuddy agent with type matching and advice.
     */
    private static void installAgent(Instrumentation inst) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .type(MethodProbeAgent::shouldInstrument)
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(
                            DynamicType.Builder<?> builder,
                            TypeDescription typeDescription,
                            ClassLoader classLoader,
                            JavaModule module,
                            ProtectionDomain protectionDomain) {

                        return builder.visit(
                                Advice.to(ProbeAdvice.class)
                                        .on(ElementMatchers.isMethod()
                                                .and(ElementMatchers.not(ElementMatchers.isConstructor()))
                                                .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                                                .and(ElementMatchers.not(ElementMatchers.isNative()))));
                    }
                })
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription typeDescription,
                            ClassLoader classLoader,
                            JavaModule module,
                            boolean loaded,
                            DynamicType dynamicType) {
                        System.out.println("[MethodProbe] Transformed: " + typeDescription.getName());
                    }

                    @Override
                    public void onError(String typeName,
                            ClassLoader classLoader,
                            JavaModule module,
                            boolean loaded,
                            Throwable throwable) {
                        System.err.println("[MethodProbe] Error transforming: " + typeName);
                        throwable.printStackTrace();
                    }
                })
                .installOn(inst);
    }

    /**
     * Check if a type should be instrumented based on configuration.
     */
    private static boolean shouldInstrument(TypeDescription typeDescription) {
        String className = typeDescription.getName();
        return AgentConfig.shouldInstrumentClass(className);
    }

    /**
     * Get the instrumentation instance for retransformation.
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Retransform classes after configuration change.
     */
    public static void retransformClasses() {
        if (instrumentation == null) {
            return;
        }

        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            String className = loadedClass.getName();
            if (AgentConfig.shouldInstrumentClass(className) &&
                    instrumentation.isModifiableClass(loadedClass)) {
                try {
                    instrumentation.retransformClasses(loadedClass);
                    System.out.println("[MethodProbe] Retransformed: " + className);
                } catch (Exception e) {
                    System.err.println("[MethodProbe] Failed to retransform: " + className);
                    e.printStackTrace();
                }
            }
        }
    }
}
