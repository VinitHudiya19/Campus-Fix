package com.campusfix.activity;

import com.campusfix.request.ServiceRequest;
import com.campusfix.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes the request timeline.
 *
 * <p>Every service that changes a request calls this. Keeping the writing in one
 * place means the timeline cannot quietly develop gaps because one code path
 * forgot to record itself.
 *
 * <p>Every method joins the caller's existing transaction ({@code MANDATORY} is
 * not used, but there is no {@code REQUIRES_NEW} either) — so if the change
 * rolls back, its log entry rolls back with it and the history never claims
 * something happened that did not.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final Clock clock;

    public ActivityLogService(ActivityLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void recordCreated(ServiceRequest request, User actor) {
        save(request, actor, ActivityType.REQUEST_CREATED, null,
                request.getStatus().name(),
                actor.getFullName() + " reported this problem");
    }

    @Transactional
    public void recordAssigned(ServiceRequest request, User actor, User technician, String note) {
        String message = actor.getFullName() + " assigned this to " + technician.getFullName();
        save(request, actor, ActivityType.ASSIGNED, null, technician.getFullName(),
                note == null ? message : message + " — " + note);
    }

    @Transactional
    public void recordUnassigned(ServiceRequest request, User actor, User previousTechnician) {
        save(request, actor, ActivityType.UNASSIGNED, previousTechnician.getFullName(), null,
                actor.getFullName() + " removed " + previousTechnician.getFullName()
                        + " from this request");
    }

    @Transactional
    public void recordStatusChange(ServiceRequest request, User actor,
                                   String from, String to, String message) {
        save(request, actor, ActivityType.STATUS_CHANGED, from, to, message);
    }

    /** No actor: the scheduled check found this, not a person. */
    @Transactional
    public void recordSystemEvent(ServiceRequest request, ActivityType type, String message) {
        save(request, null, type, null, null, message);
    }

    private void save(ServiceRequest request, User actor, ActivityType type,
                      String oldValue, String newValue, String message) {
        repository.save(new ActivityLog(request, actor, type, oldValue, newValue, message, clock.instant()));
    }
}
