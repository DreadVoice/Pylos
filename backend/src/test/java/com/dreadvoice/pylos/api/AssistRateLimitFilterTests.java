package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.agent.AgentResponse;
import com.dreadvoice.pylos.agent.CopilotAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "pylos.api.token=correct-horse-battery-staple",
        "pylos.api.assist.per-client-limit=2",
        "pylos.api.assist.per-client-window=1m",
        "pylos.api.assist.global-limit=1000",
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class AssistRateLimitFilterTests {

    private static final String TOKEN = "correct-horse-battery-staple";

    private static final String BODY = """
            {"context":{"site_type":"DEMO","url":"http://localhost:8080/demo/chat",
             "title":"demo","text":"hello","hints":{"invoice_ids":[],"dispute_ids":[]}},
             "instruction":"Invoice this conversation."}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CopilotAgent copilotAgent;

    @MockitoBean
    private ActionService actionService;

    private void agentAnswers() {
        when(copilotAgent.assist(any(), any()))
                .thenReturn(new AgentResponse("done", List.of(), false));
    }

    @Test
    void refusesTheThirdAssistFromTheSameClientWithinTheWindow() throws Exception {
        agentAnswers();

        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(assist("203.0.113.9")).andExpect(status().isOk());
        }

        mockMvc.perform(assist("203.0.113.9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Too many")))
                .andExpect(jsonPath("$.retry_after_seconds").exists());

        verify(copilotAgent, times(2)).assist(any(), any());
    }

    @Test
    void countsClientsSeparatelySoOneJudgeCannotBlockAnother() throws Exception {
        agentAnswers();

        mockMvc.perform(assist("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(assist("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(assist("203.0.113.10")).andExpect(status().isTooManyRequests());

        mockMvc.perform(assist("203.0.113.11")).andExpect(status().isOk());
    }

    @Test
    void anUnauthenticatedFloodCannotUseUpTheLimit() throws Exception {
        agentAnswers();

        for (int attempt = 0; attempt < 20; attempt++) {
            mockMvc.perform(post("/api/assist")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.12");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isUnauthorized());
        }

        verify(copilotAgent, never()).assist(any(), any());

        mockMvc.perform(assist("203.0.113.12")).andExpect(status().isOk());
        mockMvc.perform(assist("203.0.113.12")).andExpect(status().isOk());
    }

    @Test
    void leavesTheOtherEndpointsAlone() throws Exception {
        when(actionService.list(any())).thenReturn(List.of());

        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(get("/api/actions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.13");
                                return request;
                            }))
                    .andExpect(status().isOk());
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder assist(String clientIp) {
        return post("/api/assist")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }
}
