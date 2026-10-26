package com.dreadvoice.pylos.config;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.agent.CopilotAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "pylos.api.token=correct-horse-battery-staple",
        "pylos.api.allowed-origins=chrome-extension://*,https://demo.pylos.example.com",
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class ApiSecurityTests {

    private static final String TOKEN = "correct-horse-battery-staple";
    private static final String EXTENSION_ORIGIN = "chrome-extension://abcdefghijklmnop";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActionService actionService;

    @MockitoBean
    private CopilotAgent copilotAgent;

    @Test
    void rejectsApiRequestsWithNoToken() throws Exception {
        mockMvc.perform(get("/api/actions"))
                .andExpect(status().isUnauthorized());

        verify(actionService, never()).list(isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer wrong-token",
            "Bearer ",
            "Basic correct-horse-battery-staple",
            "correct-horse-battery-staple",
            "Bearer correct-horse-battery-stapl"
    })
    void rejectsApiRequestsWithABadAuthorizationHeader(String headerValue) throws Exception {
        mockMvc.perform(get("/api/actions").header(HttpHeaders.AUTHORIZATION, headerValue))
                .andExpect(status().isUnauthorized());

        verify(actionService, never()).list(isNull());
    }

    @Test
    void acceptsApiRequestsWithTheConfiguredToken() throws Exception {
        when(actionService.list(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/actions").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void protectsTheApprovalEndpointWhichMovesMoney() throws Exception {
        mockMvc.perform(post("/api/actions/11111111-2222-3333-4444-555555555555/approve"))
                .andExpect(status().isUnauthorized());

        verify(actionService, never()).approve(any(), any());
    }

    @Test
    void protectsTheAssistEndpointWhichSpendsLlmQuota() throws Exception {
        mockMvc.perform(post("/api/assist"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(copilotAgent);
    }

    @Test
    void allowsPreflightFromAnAllowedExtensionOrigin() throws Exception {
        mockMvc.perform(options("/api/assist")
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN));
    }

    @Test
    void refusesPreflightFromAnUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/assist")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotAdvertiseCredentialSupportToBrowsers() throws Exception {
        mockMvc.perform(options("/api/assist")
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
