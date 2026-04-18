package io.agentbrain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class AppConfig {

    @Value("${agentbrain.claude-dir}")
    private String claudeDir;

    @Bean
    public File claudeDirFile() {
        File dir = new File(claudeDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
