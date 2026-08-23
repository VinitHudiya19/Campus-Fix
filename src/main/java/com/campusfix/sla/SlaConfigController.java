package com.campusfix.sla;

import com.campusfix.request.Priority;
import com.campusfix.sla.dto.SlaConfigRequest;
import com.campusfix.sla.dto.SlaConfigResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sla")
public class SlaConfigController {

    private final SlaService slaService;
    private final EscalationService escalationService;

    public SlaConfigController(SlaService slaService, EscalationService escalationService) {
        this.slaService = slaService;
        this.escalationService = escalationService;
    }

    /**
     * Readable by any signed-in user: a student is entitled to know how long the
     * college says a high-priority problem should take.
     */
    @GetMapping
    public List<SlaConfigResponse> list() {
        return slaService.findAll();
    }

    /** Admin only — this is college policy, not a per-request setting. */
    @PutMapping("/{priority}")
    public SlaConfigResponse update(@PathVariable Priority priority,
                                    @Valid @RequestBody SlaConfigRequest request) {
        return slaService.update(priority, request);
    }

    /**
     * Runs the overdue check immediately instead of waiting for the timer.
     *
     * <p>Exists so the escalation rules can be demonstrated and tested without
     * a fifteen-minute wait. It is safe to call repeatedly — an already
     * escalated request is skipped.
     */
    @PostMapping("/check-now")
    public Map<String, Integer> checkNow() {
        return Map.of("escalated", escalationService.escalateOverdue());
    }
}
