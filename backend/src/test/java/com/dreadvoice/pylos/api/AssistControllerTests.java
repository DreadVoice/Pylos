package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.action.ActionType;
import com.dreadvoice.pylos.action.PendingAction;
import com.dreadvoice.pylos.agent.AgentResponse;
import com.dreadvoice.pylos.agent.CopilotAgent;
import com.dreadvoice.pylos.agent.LlmNotConfiguredException;
import com.dreadvoice.pylos.context.PageContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistController.class)
@Import(ActionViewMapper.class)
class AssistControllerTests {

    private static final UUID ACTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final String CHAT_REQUEST = """
            {"context":{"site_type":"CHAT","url":"https://web.whatsapp.com/thread/1",
             "title":"Buyer chat","text":"Buyer: two hoodies please\\nSeller: 25 each"},
             "instruction":"Invoice this"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CopilotAgent copilotAgent;

    @MockitoBean
    private ActionService actionService;

    @Test
    void returnsTheAssistantMessageAndTheProposedActions() throws Exception {
        when(copilotAgent.assist(any(), any())).thenReturn(
                new AgentResponse("I have proposed an invoice for USD 50.00.", List.of(ACTION_ID), false));
        when(actionService.get(ACTION_ID)).thenReturn(proposed());

        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("I have proposed an invoice for USD 50.00."))
                .andExpect(jsonPath("$.context_truncated").value(false))
                .andExpect(jsonPath("$.proposed_actions.length()").value(1))
                .andExpect(jsonPath("$.proposed_actions[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.proposed_actions[0].action_type").value("SEND_INVOICE"))
                .andExpect(jsonPath("$.proposed_actions[0].payload.currency_code").value("USD"));
    }

    @Test
    void passesThePageContextThroughToTheAgent() throws Exception {
        when(copilotAgent.assist(any(), any()))
                .thenReturn(new AgentResponse("Noted.", List.of(), false));

        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST))
                .andExpect(status().isOk());

        ArgumentCaptor<PageContext> context = ArgumentCaptor.forClass(PageContext.class);
        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(copilotAgent).assist(context.capture(), instruction.capture());

        assertThat(context.getValue().siteType().name()).isEqualTo("CHAT");
        assertThat(context.getValue().text()).contains("two hoodies");
        assertThat(instruction.getValue()).isEqualTo("Invoice this");
    }

    @Test
    void reportsTruncatedPageContent() throws Exception {
        when(copilotAgent.assist(any(), any()))
                .thenReturn(new AgentResponse("That was a long conversation.", List.of(), true));

        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context_truncated").value(true));
    }

    @Test
    void rejectsARequestWithoutAPageContext() throws Exception {
        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"Invoice this\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(copilotAgent);
    }

    @Test
    void rejectsAnUnknownSiteType() throws Exception {
        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"context":{"site_type":"NOT_A_SITE","text":"hello"},
                                 "instruction":"Invoice this"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(copilotAgent);
    }

    @Test
    void rejectsAnOversizedInstruction() throws Exception {
        String instruction = "x".repeat(2100);

        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"context":{"site_type":"CHAT","text":"hello"},
                                 "instruction":"%s"}
                                """.formatted(instruction)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(copilotAgent);
    }

    @Test
    void returnsServiceUnavailableWhenNoModelIsConfigured() throws Exception {
        when(copilotAgent.assist(any(), any())).thenThrow(new LlmNotConfiguredException());

        mockMvc.perform(post("/api/assist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "No language model is configured. Set GEMINI_API_KEY to enable the copilot."));
    }

    private static PendingAction proposed() {
        return new PendingAction(
                ActionType.SEND_INVOICE,
                """
                {"recipient_email":"buyer@personal.example.com","currency_code":"USD",
                 "items":[{"name":"Hoodie","quantity":"2","unit_amount":"25.00"}]}
                """,
                "Invoice USD 50.00 to buyer@personal.example.com (1 item)",
                "action-1");
    }
}
