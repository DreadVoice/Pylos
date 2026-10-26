package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.agent.AgentResponse;
import com.dreadvoice.pylos.agent.CopilotAgent;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assist")
public class AssistController {

    private final CopilotAgent copilotAgent;
    private final ActionService actionService;
    private final ActionViewMapper actionViewMapper;

    public AssistController(CopilotAgent copilotAgent,
                            ActionService actionService,
                            ActionViewMapper actionViewMapper) {
        this.copilotAgent = copilotAgent;
        this.actionService = actionService;
        this.actionViewMapper = actionViewMapper;
    }

    @PostMapping
    public AssistResponse assist(@Valid @RequestBody AssistRequest request) {
        AgentResponse response = copilotAgent.assist(request.context(), request.instruction());

        List<ActionView> proposedActions = response.proposedActionIds().stream()
                .map(actionService::get)
                .map(actionViewMapper::toView)
                .toList();

        return new AssistResponse(response.message(), proposedActions, response.contextTruncated());
    }
}
