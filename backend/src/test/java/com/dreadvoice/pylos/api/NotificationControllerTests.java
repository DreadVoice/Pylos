package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.notification.Notification;
import com.dreadvoice.pylos.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTests {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationRepository repository;

    private static Notification paid() {
        return new Notification("WH-1", "INVOICING.INVOICE.PAID", "Invoice 0058 was paid",
                "INV2-GS4Q-CBHK-S6QM-CC5V", "USD", "60.00", Instant.parse("2026-11-04T09:00:00Z"));
    }

    @Test
    void listsRecentNotificationsWithTheUnreadCount() throws Exception {
        when(repository.findTop50ByOrderByOccurredAtDesc()).thenReturn(List.of(paid()));
        when(repository.countByReadAtIsNull()).thenReturn(1L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(1))
                .andExpect(jsonPath("$.notifications[0].summary").value("Invoice 0058 was paid"))
                .andExpect(jsonPath("$.notifications[0].event_type").value("INVOICING.INVOICE.PAID"))
                .andExpect(jsonPath("$.notifications[0].invoice_id").value("INV2-GS4Q-CBHK-S6QM-CC5V"))
                .andExpect(jsonPath("$.notifications[0].amount").value("60.00"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    @Test
    void listsOnlyUnreadNotificationsWhenAsked() throws Exception {
        when(repository.findByReadAtIsNullOrderByOccurredAtDesc()).thenReturn(List.of(paid()));
        when(repository.countByReadAtIsNull()).thenReturn(1L);

        mockMvc.perform(get("/api/notifications").param("unread", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1));

        verify(repository).findByReadAtIsNullOrderByOccurredAtDesc();
    }

    @Test
    void marksEverythingReadWithTheConfiguredClock() throws Exception {
        when(repository.findTop50ByOrderByOccurredAtDesc()).thenReturn(List.of());
        when(repository.countByReadAtIsNull()).thenReturn(0L);

        mockMvc.perform(post("/api/notifications/mark-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count").value(0));

        verify(repository).markAllRead(NOW);
    }

    @Test
    void answersWithAnEmptyListRatherThanNothingAtAll() throws Exception {
        when(repository.findTop50ByOrderByOccurredAtDesc()).thenReturn(List.of());
        when(repository.countByReadAtIsNull()).thenReturn(0L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray())
                .andExpect(jsonPath("$.notifications.length()").value(0))
                .andExpect(jsonPath("$.unread_count").value(0));
    }
}
