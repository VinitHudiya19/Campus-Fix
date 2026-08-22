package com.campusfix.request;

import com.campusfix.category.Category;
import com.campusfix.category.CategoryRepository;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.location.Location;
import com.campusfix.location.LocationRepository;
import com.campusfix.request.dto.CreateRequestRequest;
import com.campusfix.request.dto.PagedResponse;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.RequestSummaryResponse;
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
import java.time.temporal.ChronoUnit;

@Service
public class ServiceRequestService {

    private final ServiceRequestRepository requestRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ServiceRequestService(ServiceRequestRepository requestRepository,
                                 CategoryRepository categoryRepository,
                                 LocationRepository locationRepository,
                                 UserRepository userRepository,
                                 CurrentUser currentUser,
                                 Clock clock) {
        this.requestRepository = requestRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
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
                now.plus(request.priority().getSlaHours(), ChronoUnit.HOURS));

        ServiceRequest saved = requestRepository.saveAndFlush(serviceRequest);
        saved.assignRequestNumber(buildRequestNumber(saved.getId(), now));

        return RequestDetailResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RequestSummaryResponse> search(RequestStatus status,
                                                        Long categoryId,
                                                        Priority priority,
                                                        Pageable pageable) {
        RequestScope scope = RequestScope.forUser(currentUser.require());

        Page<ServiceRequest> page = requestRepository.search(
                scope.studentId(), scope.departmentId(), status, categoryId, priority, pageable);

        return PagedResponse.of(page, RequestSummaryResponse::from);
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

        return RequestDetailResponse.from(request);
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
