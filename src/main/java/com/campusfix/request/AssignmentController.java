package com.campusfix.request;

import com.campusfix.request.dto.AssignRequest;
import com.campusfix.request.dto.AssignmentResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assignment lives under a request because it has no meaning on its own —
 * {@code /api/assignments/7} would be an id nobody could act on. The URL says
 * what the operation is about: this request's assignment.
 */
@RestController
@RequestMapping("/api/requests/{id}")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Returns the whole request, because assigning also changes its status. */
    @PostMapping("/assign")
    public RequestDetailResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return assignmentService.assign(id, request);
    }

    @DeleteMapping("/assignment")
    public RequestDetailResponse unassign(@PathVariable Long id) {
        return assignmentService.unassign(id);
    }

    @GetMapping("/assignments")
    public List<AssignmentResponse> history(@PathVariable Long id) {
        return assignmentService.history(id);
    }

    /** Fills the assignment dropdown for a department head, who cannot call /api/users. */
    @GetMapping("/assignable-technicians")
    public List<AssignmentService.AssignableTechnician> assignableTechnicians(@PathVariable Long id) {
        return assignmentService.assignableTechnicians(id);
    }
}
