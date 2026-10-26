package com.dreadvoice.pylos.api;

import com.dreadvoice.pylos.action.ActionService;
import com.dreadvoice.pylos.action.ActionStatus;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionService actionService;
    private final ActionViewMapper actionViewMapper;

    public ActionController(ActionService actionService, ActionViewMapper actionViewMapper) {
        this.actionService = actionService;
        this.actionViewMapper = actionViewMapper;
    }

    @GetMapping
    public List<ActionView> list(@RequestParam(required = false) ActionStatus status) {
        return actionService.list(status).stream().map(actionViewMapper::toView).toList();
    }

    @GetMapping("/{id}")
    public ActionView get(@PathVariable UUID id) {
        return actionViewMapper.toView(actionService.get(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ActionView> approve(@PathVariable UUID id,
                                              @RequestBody(required = false) ApproveActionRequest request) {
        String editedPayload = editedPayload(request);
        return ResponseEntity.ok(actionViewMapper.toView(actionService.approve(id, editedPayload)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ActionView> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(actionViewMapper.toView(actionService.reject(id)));
    }

    private String editedPayload(ApproveActionRequest request) {
        if (request == null || request.payload() == null || request.payload() instanceof NullNode) {
            return null;
        }
        return request.payload().toString();
    }
}
