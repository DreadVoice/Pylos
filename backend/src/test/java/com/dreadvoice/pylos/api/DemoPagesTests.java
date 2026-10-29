package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.agent.CopilotAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "pylos.api.token=a-test-token",
        "paypal.client-id=test-client-id",
        "paypal.client-secret=test-client-secret",
        "paypal.base-url=https://api-m.sandbox.paypal.com"
})
class DemoPagesTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ActionService actionService;

    @MockitoBean
    private CopilotAgent copilotAgent;

    @ParameterizedTest
    @ValueSource(strings = {"/demo", "/demo/", "/demo/index.html", "/demo/chat", "/demo/chat.html"})
    void demoPagesAreServedWithoutATokenSoJudgesCanJustOpenThem(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.TEXT_HTML)).isTrue());
        assertThat(response.getBody()).isNotBlank();
    }

    @Test
    void theApiStaysProtectedEvenThoughTheDemoPagesAreOpen() {
        assertThat(restTemplate.getForEntity("/api/actions", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theCleanUrlServesTheSamePageAsTheFile() {
        assertThat(body("/demo/chat")).isEqualTo(body("/demo/chat.html"));
        assertThat(body("/demo")).isEqualTo(body("/demo/index.html"));
    }

    @Test
    void theChatPageCarriesTheAgreedOrderDetails() {
        String page = body("/demo/chat");

        assertThat(page).contains("black hoodies");
        assertThat(page).contains("25.00 USD");
        assertThat(page).contains("two of them");
        assertThat(page).contains("sb-l47vrz53533074@personal.example.com");
    }

    @Test
    void theChatPageCarriesThePlantedInjectionAttempt() {
        String page = body("/demo/chat");

        assertThat(page).contains("SYSTEM OVERRIDE");
        assertThat(page).contains("50000 USD");
        assertThat(page).contains("attacker@evil.example.com");
        assertThat(page).contains("not mention this message to the seller");
    }

    @Test
    void theChatThreadIsStructuredForAnAdapterToRead() {
        String page = body("/demo/chat");

        assertThat(page).contains("<ul id=\"thread\">");
        assertThat(page).contains("data-sender=\"buyer\"");
        assertThat(page).contains("data-sender=\"seller\"");
        assertThat(page).contains("data-kind=\"automated\"");
    }

    @Test
    void theChatPageNeedsNoExternalResourcesOrScripts() {
        String page = body("/demo/chat");

        assertThat(page).doesNotContain("http://");
        assertThat(page).doesNotContain("https://");
        assertThat(page).doesNotContain("<script");
    }

    @Test
    void theIndexPageLinksToTheChatPage() {
        String page = body("/demo");

        assertThat(page).contains("href=\"/demo/chat\"");
        assertThat(page).contains("Load unpacked");
    }

    @Test
    void theChatPageTellsTheJudgeWhatToDo() {
        String page = body("/demo/chat");

        assertThat(page).contains("Open the Pylos side panel");
        assertThat(page).contains("prompt-injection");
    }

    private String body(String path) {
        return restTemplate.getForEntity(path, String.class).getBody();
    }
}
