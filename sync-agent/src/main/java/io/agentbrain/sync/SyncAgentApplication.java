package io.agentbrain.sync;

import io.agentbrain.sync.config.SyncConfig;
import io.agentbrain.sync.server.LocalApiServer;
import io.agentbrain.sync.sync.SyncClient;
import io.agentbrain.sync.watcher.ClaudeDirWatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class SyncAgentApplication {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "setup".equals(args[0])) {
            runSetup();
            return;
        }

        ConfigurableApplicationContext ctx = SpringApplication.run(SyncAgentApplication.class, args);
        SyncConfig config = ctx.getBean(SyncConfig.class);
        SyncClient syncClient = ctx.getBean(SyncClient.class);
        ClaudeDirWatcher watcher = ctx.getBean(ClaudeDirWatcher.class);
        LocalApiServer localApi = ctx.getBean(LocalApiServer.class);

        log.info("AgentBrain Sync Agent starting...");
        log.info("Claude home: {}", config.getClaudeHome());
        log.info("Server: {}", config.getServerUrl());

        // Full sync at startup
        syncClient.fullSync();

        // Start local API server for hooks
        localApi.start();

        // Start filesystem watcher
        watcher.start();

        log.info("Sync agent running. Local API on localhost:{}", config.getLocalApiPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Sync agent shutting down...");
            watcher.stop();
            localApi.stop();
        }));
    }

    private static void runSetup() throws Exception {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        java.nio.file.Path propsDir = java.nio.file.Path.of(System.getProperty("user.home"), ".agentbrain");
        java.nio.file.Files.createDirectories(propsDir);
        java.nio.file.Path propsFile = propsDir.resolve("sync-agent.properties");

        System.out.println("=== AgentBrain Sync Agent Setup ===");
        System.out.print("Server URL (e.g. http://your-server:8080): ");
        String serverUrl = scanner.nextLine().trim();
        System.out.print("Sync token (from server .env SYNC_TOKEN): ");
        String token = scanner.nextLine().trim();
        System.out.print("Claude home [" + System.getProperty("user.home") + "/.claude]: ");
        String claudeHome = scanner.nextLine().trim();
        if (claudeHome.isBlank()) claudeHome = System.getProperty("user.home") + "/.claude";

        String props = """
                server.url=%s
                sync.token=%s
                claude.home=%s
                watch.interval.ms=500
                local.api.port=7701
                """.formatted(serverUrl, token, claudeHome);

        java.nio.file.Files.writeString(propsFile, props);
        System.out.println("Config written to: " + propsFile);
        System.out.println("Start the daemon with: java -jar agentbrain-sync.jar");
    }
}
