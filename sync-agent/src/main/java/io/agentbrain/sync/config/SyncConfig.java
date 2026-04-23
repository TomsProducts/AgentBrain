package io.agentbrain.sync.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class SyncConfig {

    @Value("${server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${sync.token:}")
    private String syncToken;

    @Value("${claude.home:${user.home}/.claude}")
    private String claudeHome;

    @Value("${watch.interval.ms:500}")
    private long watchIntervalMs;

    @Value("${local.api.port:7701}")
    private int localApiPort;
}
