package io.agentbrain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "agentbrain.claude-dir=/tmp/test-claude",
        "agentbrain.sync.token=test-token"
})
class AgentBrainApplicationTests {

    @Test
    void contextLoads() {
    }
}
