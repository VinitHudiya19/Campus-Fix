package com.campusfix.request;

import java.util.EnumSet;
import java.util.Set;

/**
 * The complete list of ways a request may move, and who may move it.
 *
 * <p>This is the whole workflow in one place. Before this existed, a status was
 * just a label anyone could overwrite; here, every legal move is one row of the
 * table below and anything not listed is impossible. A request cannot jump from
 * OPEN to CLOSED, because no action does that.
 *
 * <p>The two moves caused by assignment — OPEN to ASSIGNED and back — are not
 * here. They are a side effect of giving work to somebody, not something a user
 * asks for directly, so they live in {@link AssignmentService}.
 */
public enum StatusAction {

    /** The technician picks the work up. Also used to resume after a reopen. */
    START("Start work", RequestStatus.IN_PROGRESS,
            EnumSet.of(RequestStatus.ASSIGNED, RequestStatus.REOPENED), Actor.WORKER, false),

    /** The technician says it is fixed. The student still has to agree. */
    RESOLVE("Mark as resolved", RequestStatus.RESOLVED,
            EnumSet.of(RequestStatus.IN_PROGRESS), Actor.WORKER, true),

    /** Invalid, duplicate or out of scope. A reason is required. */
    REJECT("Reject", RequestStatus.REJECTED,
            EnumSet.of(RequestStatus.OPEN, RequestStatus.ASSIGNED,
                    RequestStatus.IN_PROGRESS, RequestStatus.REOPENED), Actor.MANAGER, true),

    /** The student agrees it is fixed. This is the only route to CLOSED. */
    CONFIRM("Confirm it is fixed", RequestStatus.CLOSED,
            EnumSet.of(RequestStatus.RESOLVED), Actor.REPORTER, false),

    /** The student says it is still broken. A reason is required. */
    REOPEN("Still not fixed", RequestStatus.REOPENED,
            EnumSet.of(RequestStatus.RESOLVED), Actor.REPORTER, true);

    /**
     * Who is entitled to perform an action. Deliberately about the person's
     * relationship to <em>this</em> request, not just their role.
     */
    public enum Actor {
        /** The technician holding it, or the department head or admin above them. */
        WORKER,
        /** The head of the owning department, or an admin. */
        MANAGER,
        /** The student who filed it, and nobody else. */
        REPORTER
    }

    private final String label;
    private final RequestStatus target;
    private final Set<RequestStatus> allowedFrom;
    private final Actor actor;
    private final boolean noteRequired;

    StatusAction(String label, RequestStatus target, Set<RequestStatus> allowedFrom,
                 Actor actor, boolean noteRequired) {
        this.label = label;
        this.target = target;
        this.allowedFrom = allowedFrom;
        this.actor = actor;
        this.noteRequired = noteRequired;
    }

    public String getLabel() {
        return label;
    }

    public RequestStatus getTarget() {
        return target;
    }

    public Actor getActor() {
        return actor;
    }

    /**
     * A note is demanded exactly where a bare status change would leave somebody
     * confused: what was actually done, why it was refused, why it is still
     * broken. Starting work needs no explanation.
     */
    public boolean isNoteRequired() {
        return noteRequired;
    }

    public boolean isAllowedFrom(RequestStatus current) {
        return allowedFrom.contains(current);
    }
}
