package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.notification.WebhookEventHandler;
import com.dreadvoice.pylos.paypal.WebhookHeaders;
import com.dreadvoice.pylos.paypal.WebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/paypal")
public class PayPalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookController.class);

    private final WebhookVerifier verifier;
    private final WebhookEventHandler handler;

    public PayPalWebhookController(WebhookVerifier verifier, WebhookEventHandler handler) {
        this.verifier = verifier;
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = WebhookHeaders.AUTH_ALGO, required = false) String authAlgo,
            @RequestHeader(value = WebhookHeaders.CERT_URL, required = false) String certUrl,
            @RequestHeader(value = WebhookHeaders.TRANSMISSION_ID, required = false) String transmissionId,
            @RequestHeader(value = WebhookHeaders.TRANSMISSION_SIG, required = false) String transmissionSig,
            @RequestHeader(value = WebhookHeaders.TRANSMISSION_TIME, required = false) String transmissionTime,
            @RequestBody(required = false) String rawBody) {

        WebhookHeaders headers = new WebhookHeaders(
                authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime);

        if (!verifier.verify(headers, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        handler.handle(rawBody).ifPresent(notification ->
                log.info("Recorded webhook {} as notification {}",
                        notification.getEventType(), notification.getId()));

        return ResponseEntity.noContent().build();
    }
}
