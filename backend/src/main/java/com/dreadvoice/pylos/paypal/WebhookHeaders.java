package com.dreadvoice.pylos.paypal;

import org.springframework.util.StringUtils;

public record WebhookHeaders(
        String authAlgo,
        String certUrl,
        String transmissionId,
        String transmissionSig,
        String transmissionTime
) {

    public static final String AUTH_ALGO = "PAYPAL-AUTH-ALGO";
    public static final String CERT_URL = "PAYPAL-CERT-URL";
    public static final String TRANSMISSION_ID = "PAYPAL-TRANSMISSION-ID";
    public static final String TRANSMISSION_SIG = "PAYPAL-TRANSMISSION-SIG";
    public static final String TRANSMISSION_TIME = "PAYPAL-TRANSMISSION-TIME";

    public boolean complete() {
        return StringUtils.hasText(authAlgo)
                && StringUtils.hasText(certUrl)
                && StringUtils.hasText(transmissionId)
                && StringUtils.hasText(transmissionSig)
                && StringUtils.hasText(transmissionTime);
    }
}
