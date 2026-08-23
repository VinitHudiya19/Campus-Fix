package com.campusfix.request;

import com.campusfix.request.dto.CreateRequestRequest;
import com.campusfix.request.dto.PagedResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.RequestSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class ServiceRequestController {

    private final ServiceRequestService requestService;

    public ServiceRequestController(ServiceRequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * The list is always scoped to the caller by the service. There is no
     * {@code studentId} or {@code departmentId} parameter here on purpose —
     * accepting one would let a student ask for somebody else's requests.
     */
    @GetMapping
    public PagedResponse<RequestSummaryResponse> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return requestService.search(status, categoryId, priority, unassignedOnly, pageable);
    }

    @GetMapping("/{id}")
    public RequestDetailResponse get(@PathVariable Long id) {
        return requestService.findById(id);
    }

    @PostMapping
    public ResponseEntity<RequestDetailResponse> create(@Valid @RequestBody CreateRequestRequest request) {
        RequestDetailResponse created = requestService.create(request);
        return ResponseEntity.created(URI.create("/api/requests/" + created.id())).body(created);
    }

    /** Fills the priority dropdown, and tells the form which values a student may pick. */
    @GetMapping("/priorities")
    public List<PriorityOption> priorities() {
        return Arrays.stream(Priority.values())
                .map(p -> new PriorityOption(p.name(), p.getDisplayName(), p.getSlaHours(), p.isStudentSelectable()))
                .toList();
    }

    @GetMapping("/statuses")
    public List<StatusOption> statuses() {
        return Arrays.stream(RequestStatus.values())
                .map(s -> new StatusOption(s.name(), s.getDisplayName()))
                .toList();
    }

    public record PriorityOption(String value, String label, int slaHours, boolean studentSelectable) {
    }

    public record StatusOption(String value, String label) {
    }
}
