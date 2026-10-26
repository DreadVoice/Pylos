package com.dreadvoice.pylos.config;

import com.dreadvoice.pylos.action.ActionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "pylos.api.token=",
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class ApiSecurityFailClosedTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActionService actionService;

    @Test
    void anUnconfiguredTokenRejectsEverythingRatherThanAllowingIt() throws Exception {
        mockMvc.perform(get("/api/actions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/actions").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/actions").header(HttpHeaders.AUTHORIZATION, "Bearer anything"))
                .andExpect(status().isUnauthorized());

        verify(actionService, never()).list(isNull());
        verify(actionService, never()).approve(any(), any());
    }
}
