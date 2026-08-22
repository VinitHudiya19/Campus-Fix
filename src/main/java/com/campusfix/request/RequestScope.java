package com.campusfix.request;

import com.campusfix.common.security.AuthenticatedUser;

/**
 * Which requests the signed-in user is allowed to see, expressed as the two
 * filters the repository query understands. A null field means "no restriction
 * on this dimension".
 *
 * <p>Turning the role into a scope in one place, rather than writing
 * {@code if (role == STUDENT)} inside each query method, means a new role only
 * has to be handled here.
 */
public record RequestScope(Long studentId, Long departmentId) {

    private static final RequestScope EVERYTHING = new RequestScope(null, null);

    public static RequestScope forUser(AuthenticatedUser user) {
        return switch (user.role()) {
            // A student sees the requests they reported, and nothing else.
            case STUDENT -> new RequestScope(user.id(), null);

            // Staff see their own department's work. A technician's view narrows
            // to "assigned to me" in Phase 7, once assignments exist; until then
            // the department queue is the closest honest answer.
            case TECHNICIAN, DEPARTMENT_HEAD -> new RequestScope(null, user.departmentId());

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
        return true;
    }
}
