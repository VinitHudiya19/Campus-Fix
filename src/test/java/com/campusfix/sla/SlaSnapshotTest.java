package com.campusfix.sla;

import com.campusfix.category.Category;
import com.campusfix.department.Department;
import com.campusfix.request.Priority;
import com.campusfix.request.RequestStatus;
import com.campusfix.request.ServiceRequest;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure date arithmetic, so no mocks and no Spring. A 48-hour window opening at
 * 10:00 on the 23rd is due at 10:00 on the 25th, and warns three quarters of the
 * way through, at 22:00 on the 24th.
 */
class SlaSnapshotTest {

    private static final Instant REPORTED = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant DUE = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant WARNING_POINT = Instant.parse("2026-08-24T22:00:00Z");

    @Test
    void plentyOfTimeLeftIsOnTrack() {
        assertThat(stateAt(Instant.parse("2026-08-23T12:00:00Z"))).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    void pastThreeQuartersOfTheWindowItIsDueSoon() {
        assertThat(stateAt(WARNING_POINT.minusSeconds(1))).isEqualTo(SlaState.ON_TRACK);
        assertThat(stateAt(WARNING_POINT)).isEqualTo(SlaState.DUE_SOON);
        assertThat(stateAt(DUE.minusSeconds(1))).isEqualTo(SlaState.DUE_SOON);
    }

    @Test
    void theDeadlineItselfCountsAsBreached() {
        assertThat(stateAt(DUE)).isEqualTo(SlaState.BREACHED);
        assertThat(stateAt(DUE.plusSeconds(3600))).isEqualTo(SlaState.BREACHED);
    }

    @Test
    void aFinishedRequestIsJudgedOnWhenItFinished_notOnTheClock() {
        ServiceRequest inTime = resolvedAt(DUE.minusSeconds(3600));
        ServiceRequest late = resolvedAt(DUE.plusSeconds(3600));

        // Checked a week later: the verdict does not drift with time.
        Instant muchLater = DUE.plusSeconds(604800);
        assertThat(snapshotAt(muchLater).stateOf(inTime)).isEqualTo(SlaState.MET);
        assertThat(snapshotAt(muchLater).stateOf(late)).isEqualTo(SlaState.MISSED);
    }

    /**
     * The real code moves a request to RESOLVED through a package-private method
     * on the entity, deliberately unreachable from here. This test only needs
     * the resulting state, so it sets the two fields directly.
     */
    private ServiceRequest resolvedAt(Instant when) {
        ServiceRequest request = request();
        ReflectionTestUtils.setField(request, "status", RequestStatus.RESOLVED);
        ReflectionTestUtils.setField(request, "resolvedAt", when);
        return request;
    }

    private SlaState stateAt(Instant now) {
        return snapshotAt(now).stateOf(request());
    }

    private SlaSnapshot snapshotAt(Instant now) {
        return new SlaSnapshot(Map.of(Priority.MEDIUM, 75), now);
    }

    private ServiceRequest request() {
        Department department = new Department("IT Support", null);
        Category category = new Category("Wi-Fi", null, department);
        User student = new User("Priya Nair", "priya@college.edu", "hash", Role.STUDENT, null);

        ServiceRequest request = new ServiceRequest("Wi-Fi down", "No internet",
                student, category, null, Priority.MEDIUM, DUE);
        // createdAt is normally written by Hibernate, and the warning point is
        // measured from it, so the test has to supply it.
        ReflectionTestUtils.setField(request, "createdAt", REPORTED);
        return request;
    }
}
