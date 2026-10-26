package com.dreadvoice.pylos.agent;

public class LlmNotConfiguredException extends RuntimeException {

    public LlmNotConfiguredException() {
        super("No language model is configured. Set GEMINI_API_KEY to enable the copilot.");
    }
}
