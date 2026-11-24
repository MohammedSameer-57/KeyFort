package org.keyfort.server.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Demo Runner Disabled in Non-Demo Profiles")
class DemoRunnerDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API demo endpoints return 404 when demo profile is disabled")
    void testDemoEndpointsDisabled() throws Exception {
        mockMvc.perform(get("/api/demo/state"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/demo/create-link"))
                .andExpect(status().isNotFound());
    }
}
