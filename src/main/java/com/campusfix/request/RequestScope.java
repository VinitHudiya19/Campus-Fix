package com.campusfix.request;

import com.campusfix.common.security.AuthenticatedUser;

/**
 * Which requests the signed-in user is allowed to see, expressed as the filters
 * the repository query understands. A null field means "no restriction on this
 * dimension".
 *
 * <p>Turning the role into a scope in one place, rather than writing
 * {@code if (role == STUDENT)} inside each query method, means a new role only
 * has to be handled here.
 */
public record RequestScope(Long studentId, Long departmentId, Long technicianId) {

    private static final RequestScope EVERYTHING = new RequestScope(null, null, null);

    public static RequestScope forUser(AuthenticatedUser user) {
        return switch (user.role()) {
            // A student sees the requests they reported, and nothing else.
            case STUDENT -> new RequestScope(user.id(), null, null);

            // Since Phase 7 a technician sees the work actually given to them,
            // not the whole department queue. Deciding who does what is the
            // department head's job, so the queue is not a technician's problem.
            case TECHNICIAN -> new RequestScope(null, null, user.id());

            // A head runs the department, so they see everything in it —
            // including the unassigned requests waiting to be given out.
            case DEPARTMENT_HEAD -> new RequestScope(null, user.departmentId(), null);

            case ADMIN -> EVERYTHING;
        };
    }

    /**
     * The same rule applied to a single request, for the detail endpoint. Kept
     * next to {@link #forUser} so the list view and the detail view can never
     * disagree about who may see what.
     */
    public boolean permits(ServiceRequest request) {
        if (studentId != null && !studentId.equals(request.getStudent().getId())) {
            return false;
        }
        if (departmentId != null
                && !departmentId.equals(request.getCategory().getDepartment().getId())) {
            return false;
        }
        if (technicianId != null) {
            var assignee = request.getAssignedTechnician();
            return assignee != null && technicianId.equals(assignee.getId());
        }
        return true;
    }
}
