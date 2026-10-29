package com.dreadvoice.pylos.agent;

import com.dreadvoice.pylos.context.ContextSanitizer;
import com.dreadvoice.pylos.context.PageContext;
import com.dreadvoice.pylos.context.SanitizedPageContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class CopilotAgent {

    private static final Logger log = LoggerFactory.getLogger(CopilotAgent.class);
    private static final String INSTRUCTION_HEADING = "The seller asks:";
    private static final String NO_REPLY_FALLBACK =
            "The copilot did not return an answer for this page. Try asking again, "
                    + "or say what you want invoiced.";

    private final ChatModel chatModel;
    private final ReadTools readTools;
    private final ProposeTools proposeTools;
    private final ContextSanitizer sanitizer;
    private final PromptTemplates promptTemplates;
    private final ProposedActionTracker tracker;

    public CopilotAgent(ChatModel chatModel,
                        ReadTools readTools,
                        ProposeTools proposeTools,
                        ContextSanitizer sanitizer,
                        PromptTemplates promptTemplates,
                        ProposedActionTracker tracker) {
        this.chatModel = chatModel;
        this.readTools = readTools;
        this.proposeTools = proposeTools;
        this.sanitizer = sanitizer;
        this.promptTemplates = promptTemplates;
        this.tracker = tracker;
    }

    public AgentResponse assist(PageContext pageContext, String instruction) {
        SanitizedPageContext sanitized = sanitizer.sanitize(pageContext);
        String systemPrompt = promptTemplates.systemPrompt(sanitized.siteType());
        String userMessage = sanitizer.toDelimitedBlock(sanitized)
                + "\n\n" + INSTRUCTION_HEADING + "\n"
                + (StringUtils.hasText(instruction) ? instruction.strip() : "What should I do here?");

        tracker.start();
        try {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .tools(readTools, proposeTools)
                    .systemMessageProvider(memoryId -> systemPrompt)
                    .build();

            String message = assistant.chat(userMessage);
            List<java.util.UUID> proposed = tracker.collect();
            log.info("Assist on {} produced {} proposals", sanitized.siteType(), proposed.size());

            return new AgentResponse(
                    StringUtils.hasText(message) ? message.strip() : NO_REPLY_FALLBACK,
                    proposed,
                    sanitized.truncated());
        } finally {
            tracker.collect();
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }
}
