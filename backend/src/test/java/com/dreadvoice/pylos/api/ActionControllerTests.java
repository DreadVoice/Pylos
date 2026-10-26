package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionNotFoundException;
import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.action.ActionStateException;
import com.dreadvoice.pylos.action.ActionStatus;
import com.dreadvoice.pylos.action.ActionType;
import com.dreadvoice.pylos.action.ActionValidationException;
import com.dreadvoice.pylos.action.PendingAction;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActionController.class)
@Import(ActionViewMapper.class)
class ActionControllerTests {

    private static final UUID ACTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActionService actionService;

    @Test
    void listsActionsWithThePayloadAsAJsonObject() throws Exception {
        when(actionService.list(isNull())).thenReturn(List.of(proposed()));

        mockMvc.perform(get("/api/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action_type").value("SEND_INVOICE"))
                .andExpect(jsonPath("$[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$[0].summary").value("Invoice USD 250.00 to Test Buyer"))
                .andExpect(jsonPath("$[0].payload.currency_code").value("USD"))
                .andExpect(jsonPath("$[0].payload.items[0].unit_amount").value("250.00"));
    }

    @Test
    void filtersTheListByStatus() throws Exception {
        when(actionService.list(ActionStatus.PROPOSED)).thenReturn(List.of(proposed()));

        mockMvc.perform(get("/api/actions").param("status", "PROPOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(actionService).list(ActionStatus.PROPOSED);
    }

    @Test
    void approveWithoutABodyPassesNoEditedPayload() throws Exception {
        when(actionService.approve(eq(ACTION_ID), isNull())).thenReturn(executed());

        mockMvc.perform(post("/api/actions/{id}/approve", ACTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.result_reference").value("INV2-NEW"));

        verify(actionService).approve(ACTION_ID, null);
    }

    @Test
    void approveForwardsAnEditedPayloadAsJsonText() throws Exception {
        when(actionService.approve(eq(ACTION_ID), any())).thenReturn(executed());

        mockMvc.perform(post("/api/actions/{id}/approve", ACTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"recipient_email":"other@personal.example.com",
                                 "currency_code":"USD",
                                 "items":[{"name":"Logo","quantity":"1","unit_amount":"300.00"}]}}
                                """))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> edited = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(actionService).approve(eq(ACTION_ID), edited.capture());
        assertThat(edited.getValue()).contains("other@personal.example.com");
        assertThat(edited.getValue()).contains("300.00");
    }

    @Test
    void approveTreatsAnExplicitNullPayloadAsNoEdit() throws Exception {
        when(actionService.approve(eq(ACTION_ID), isNull())).thenReturn(executed());

        mockMvc.perform(post("/api/actions/{id}/approve", ACTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":null}"))
                .andExpect(status().isOk());

        verify(actionService).approve(ACTION_ID, null);
    }

    @Test
    void rejectReturnsTheRejectedAction() throws Exception {
        PendingAction rejected = proposed();
        rejected.setStatus(ActionStatus.REJECTED);
        when(actionService.reject(ACTION_ID)).thenReturn(rejected);

        mockMvc.perform(post("/api/actions/{id}/reject", ACTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void returnsNotFoundForAnUnknownAction() throws Exception {
        when(actionService.get(ACTION_ID)).thenThrow(new ActionNotFoundException(ACTION_ID));

        mockMvc.perform(get("/api/actions/{id}", ACTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No pending action with id " + ACTION_ID));
    }

    @Test
    void returnsConflictWhenTheActionIsNoLongerDecidable() throws Exception {
        when(actionService.approve(eq(ACTION_ID), isNull()))
                .thenThrow(new ActionStateException("Action " + ACTION_ID
                        + " is REJECTED and can no longer be approved"));

        mockMvc.perform(post("/api/actions/{id}/approve", ACTION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Action " + ACTION_ID + " is REJECTED and can no longer be approved"));
    }

    @Test
    void returnsUnprocessableEntityWithViolationsWhenValidationFails() throws Exception {
        when(actionService.approve(eq(ACTION_ID), any()))
                .thenThrow(new ActionValidationException(List.of(
                        "items[0].unitAmount: must not be negative",
                        "currencyCode: GBP is not a supported currency")));

        mockMvc.perform(post("/api/actions/{id}/approve", ACTION_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Action payload failed validation"))
                .andExpect(jsonPath("$.violations.length()").value(2))
                .andExpect(jsonPath("$.violations[0]")
                        .value("items[0].unitAmount: must not be negative"));
    }

    private static PendingAction proposed() {
        return new PendingAction(
                ActionType.SEND_INVOICE,
                """
                {"recipient_email":"buyer@personal.example.com","currency_code":"USD",
                 "items":[{"name":"Logo design","quantity":"1","unit_amount":"250.00"}]}
                """,
                "Invoice USD 250.00 to Test Buyer",
                "action-1");
    }

    private static PendingAction executed() {
        PendingAction action = proposed();
        action.setStatus(ActionStatus.EXECUTED);
        action.setResultReference("INV2-NEW");
        return action;
    }
}
