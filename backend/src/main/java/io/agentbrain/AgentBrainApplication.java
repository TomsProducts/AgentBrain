package io.agentbrain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentBrainApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentBrainApplication.class, args);
    }
}
