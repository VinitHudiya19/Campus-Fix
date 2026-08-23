package com.campusfix.request;

import com.campusfix.activity.ActivityLogService;
import com.campusfix.category.Category;
import com.campusfix.category.CategoryRepository;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.InvalidRequestException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.location.Location;
import com.campusfix.location.LocationRepository;
import com.campusfix.request.dto.CreateRequestRequest;
import com.campusfix.request.dto.PagedResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.RequestSummaryResponse;
import com.campusfix.sla.SlaService;
import com.campusfix.sla.SlaSnapshot;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class ServiceRequestService {

    /** What a client may order the list by. Anything else is a 400. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "dueAt", "priority", "status", "requestNumber", "title", "updatedAt");

    private final ServiceRequestRepository requestRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLog;
    private final SlaService slaService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ServiceRequestService(ServiceRequestRepository requestRepository,
                                 CategoryRepository categoryRepository,
                                 LocationRepository locationRepository,
                                 UserRepository userRepository,
                                 ActivityLogService activityLog,
                                 SlaService slaService,
                                 CurrentUser currentUser,
                                 Clock clock) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.slaService = slaService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public RequestDetailResponse create(CreateRequestRequest request) {
        AuthenticatedUser signedIn = currentUser.require();

        if (signedIn.role() != Role.STUDENT) {
            throw new BusinessRuleException("Only students can report a problem");
        }
        if (!request.priority().isStudentSelectable()) {
            throw new BusinessRuleException(
                    request.priority().getDisplayName() + " priority can only be set by staff");
        }

        User student = userRepository.findById(signedIn.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", signedIn.id()));
        Category category = activeCategory(request.categoryId());
        Location location = optionalActiveLocation(request.locationId());

        Instant now = clock.instant();
        ServiceRequest serviceRequest = new ServiceRequest(
                request.title().trim(),
                request.description().trim(),
                student,
                category,
                location,
                request.priority(),
                // The deadline comes from the configurable SLA table now, not
                // from a constant. Existing requests keep the date they were
                // given, so changing the policy never rewrites a past promise.
                slaService.deadlineFor(request.priority(), now));

        ServiceRequest saved = requestRepository.saveAndFlush(serviceRequest);
        saved.assignRequestNumber(buildRequestNumber(saved.getId(), now));
        activityLog.recordCreated(saved, student);

        return RequestDetailResponse.from(saved, slaService.stateOf(saved));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RequestSummaryResponse> search(RequestStatus status,
                                                        Long categoryId,
                                                        Priority priority,
                                                        boolean unassignedOnly,
                                                        String searchText,
                                                        Pageable pageable) {
        RequestScope scope = RequestScope.forUser(currentUser.require());
        requireSortableFields(pageable);

        Page<ServiceRequest> page = requestRepository.search(
                scope.studentId(), scope.departmentId(), scope.technicianId(),
                status, categoryId, priority, unassignedOnly,
                searchPattern(searchText), pageable);

        // One snapshot for the whole page, so every row on screen is judged
        // against the same instant and the same settings.
        SlaSnapshot sla = slaService.snapshot();
        return PagedResponse.of(page, request -> RequestSummaryResponse.from(request, sla.stateOf(request)));
    }

    /**
     * A request outside the caller's scope reports 404, not 403.
     *
     * <p>403 would confirm that the id exists, which lets anyone walk the id
     * range and learn how many requests the college has and when they were
     * filed. From outside the scope, the request simply is not there.
     */
    @Transactional(readOnly = true)
    public RequestDetailResponse findById(Long id) {
        RequestScope scope = RequestScope.forUser(currentUser.require());

        ServiceRequest request = requestRepository.findByIdWithDetail(id)
                .filter(scope::permits)
                .orElseThrow(() -> new ResourceNotFoundException("Request", id));

        return RequestDetailResponse.from(request, slaService.stateOf(request));
    }

    /**
     * Turns what the user typed into a LIKE pattern.
     *
     * <p>The wildcards are escaped. Without this, searching for "50%" would ask
     * the database for "50 followed by anything", and a search for "_" would
     * match every request — the user would see results they cannot explain
     * rather than the one thing they looked for.
     *
     * <p>The escape character itself is doubled first, otherwise escaping would
     * corrupt any literal "!" the user typed.
     */
    private String searchPattern(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return null;
        }
        String escaped = searchText.trim().toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * Sorting is limited to a known list.
     *
     * <p>Spring will happily sort by any property name it can resolve, so an
     * unchecked value either throws deep inside the query and surfaces as a 500,
     * or lets a caller order results by a field the API never promised. An
     * explicit list gives a clear 400 instead, and documents what is sortable.
     */
    private void requireSortableFields(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: "
                                + String.join(", ", SORTABLE_FIELDS));
            }
        });
    }

    /**
     * Built from the database id, which is already unique, so two requests
     * created at the same instant cannot collide. A separate counter table would
     * need locking to avoid exactly that race, and would still be one extra
     * write. The cost here is that the insert is flushed before the number can be
     * set — one INSERT then one UPDATE, both inside the same transaction.
     */
    private String buildRequestNumber(Long id, Instant createdAt) {
        int year = createdAt.atZone(ZoneOffset.UTC).getYear();
        return "CF-%d-%06d".formatted(year, id);
    }

    private Category activeCategory(Long categoryId) {
        Category category = categoryRepository.findByIdWithDepartment(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        if (!category.isActive()) {
            throw new BusinessRuleException(
                    "'" + category.getName() + "' is no longer accepting new requests");
        }
        return category;
    }

    private Location optionalActiveLocation(Long locationId) {
        if (locationId == null) {
            return null;
        }
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location", locationId));
        if (!location.isActive()) {
            throw new BusinessRuleException("'" + location.displayName() + "' is no longer in use");
        }
        return location;
    }
}
