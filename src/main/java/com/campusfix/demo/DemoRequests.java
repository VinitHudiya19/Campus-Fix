package com.campusfix.demo;

import com.campusfix.category.Category;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.location.Location;
import com.campusfix.request.AssignmentService;
import com.campusfix.request.Priority;
import com.campusfix.request.ServiceRequestService;
import com.campusfix.request.StatusAction;
import com.campusfix.request.WorkflowService;
import com.campusfix.request.dto.AssignRequest;
import com.campusfix.request.dto.CreateRequestRequest;
import com.campusfix.request.dto.RequestDetailResponse;
import com.campusfix.request.dto.StatusChangeRequest;
import com.campusfix.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Creates the demo requests.
 *
 * <p>These go through the <strong>real services</strong> — create, assign,
 * start, resolve, confirm — with the security context set to whichever person
 * would actually have done it. That matters: it means no seeded request can be
 * in a state the workflow forbids, every one has a genuine timeline and
 * assignment history, and the demo exercises the same code a user would.
 *
 * <p>Timestamps are then pushed into the past with plain SQL. Hibernate writes
 * {@code created_at} itself and the services read a system clock, so there is no
 * way to create a request "three days ago" through the normal path — and without
 * varied ages every request would show the same SLA state, which is exactly what
 * a demo needs to show off.
 */
@Component
@Profile("demo")
public class DemoRequests {

    @PersistenceContext
    private EntityManager entityManager;

    private final ServiceRequestService requestService;
    private final AssignmentService assignmentService;
    private final WorkflowService workflowService;

    public DemoRequests(ServiceRequestService requestService,
                        AssignmentService assignmentService,
                        WorkflowService workflowService) {
        this.requestService = requestService;
        this.assignmentService = assignmentService;
        this.workflowService = workflowService;
    }

    /**
     * One demo request.
     *
     * @param reportedHoursAgo how long ago it was filed — this is what makes the
     *                         SLA badges differ across the list
     * @param stage            how far it has been taken
     */
    private record Scenario(
            String title,
            String description,
            String category,
            String location,
            Priority priority,
            String reporter,
            String technician,
            Stage stage,
            int reportedHoursAgo,
            int actedAfterHours,
            String note) {
    }

    private enum Stage { OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, REOPENED, REJECTED }

    public void seed(Map<String, Category> categories,
                     Map<String, Location> locations,
                     Map<String, User> people) {

        for (Scenario scenario : scenarios()) {
            Long id = create(scenario, categories, locations, people);
            advance(id, scenario, people);
            backdate(id, scenario);
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * The spread is deliberate: a few of everything, some comfortably on track,
     * some about to breach, some already breached, and finished work that both
     * met and missed its target. A demo where every row looks the same shows
     * nothing.
     */
    private List<Scenario> scenarios() {
        return List.of(
                // --- still waiting for somebody to pick them up -----------------
                new Scenario("Projector will not switch on",
                        "The projector in Room 201 shows no light at all. We tried a different cable and a different laptop.",
                        "Projector", "A201", Priority.HIGH, "stu-karan", null,
                        Stage.OPEN, 40, 0, null),

                new Scenario("Printer jams on every job",
                        "The printer at the circulation desk pulls two sheets at once and then stops with a paper jam error.",
                        "Printer", "LIB-DESK", Priority.MEDIUM, "stu-ritu", null,
                        Stage.OPEN, 5, 0, null),

                new Scenario("Bench near the sports ground is broken",
                        "One of the wooden benches by the ground has a cracked plank and is not safe to sit on.",
                        "Furniture", "GROUND", Priority.LOW, "stu-zaid", null,
                        Stage.OPEN, 12, 0, null),

                new Scenario("Door lock jammed in hostel room",
                        "The lock on room 214 turns but does not open. We have been using the back latch for two days.",
                        "Room Repair", "BH-214", Priority.MEDIUM, "stu-arjun", null,
                        Stage.OPEN, 34, 0, null),

                // --- given to somebody, not started ------------------------------
                new Scenario("Ceiling fan makes a loud rattle",
                        "The fan in room 214 rattles at speeds above 3 and wobbles quite badly.",
                        "Fan", "BH-214", Priority.LOW, "stu-arjun", "tech-elec-1",
                        Stage.ASSIGNED, 10, 2, "Deepak is on that block this week"),

                new Scenario("Water supply stops every evening",
                        "Water in the Girls Hostel B first floor stops from about 7pm and comes back the next morning.",
                        "Water Supply", "GH-108", Priority.MEDIUM, "stu-meera", "tech-hostel",
                        Stage.ASSIGNED, 37, 3, null),

                new Scenario("Wi-Fi is slow at the admin reception",
                        "Pages take a long time to open at the reception desk, though the signal shows full.",
                        "Wi-Fi", "ADMIN", Priority.LOW, "stu-karan", "tech-it-2",
                        Stage.ASSIGNED, 20, 4, null),

                // --- being worked on ---------------------------------------------
                new Scenario("Wi-Fi keeps dropping in the library",
                        "The connection in the reading hall drops every few minutes and reconnects on its own. It is worst near the far windows.",
                        "Wi-Fi", "LIB-READ", Priority.MEDIUM, "stu-priya", "tech-it-1",
                        Stage.IN_PROGRESS, 30, 4, "Nearest to the library today"),

                new Scenario("No Wi-Fi at all in the hostel common room",
                        "There has been no signal in the common room since yesterday evening. The router light is orange.",
                        "Wi-Fi", "BH-COMMON", Priority.HIGH, "stu-arjun", "tech-it-1",
                        Stage.IN_PROGRESS, 28, 5, null),

                new Scenario("Fan not working in the reading hall",
                        "The fan above the second row of tables does not turn on with the switch.",
                        "Fan", "LIB-READ", Priority.MEDIUM, "stu-priya", "tech-elec-2",
                        Stage.IN_PROGRESS, 14, 3, null),

                // --- fixed, waiting for the student to agree ---------------------
                new Scenario("Lab computer will not boot",
                        "The third machine from the door in the physics lab shows a blank screen and beeps three times at startup.",
                        "Lab Computer", "SCI-PHY", Priority.HIGH, "stu-ritu", "tech-it-2",
                        Stage.RESOLVED, 20, 6, "Replaced a loose RAM stick and cleaned the slot"),

                new Scenario("Classroom was not cleaned",
                        "Room 102 still had yesterday's rubbish and the board was not wiped when the morning class started.",
                        "Cleanliness", "A102", Priority.LOW, "stu-zaid", "tech-fac",
                        Stage.RESOLVED, 12, 4, "Cleaned the room and spoke to the morning staff"),

                // --- finished ----------------------------------------------------
                new Scenario("Water leaking under the sink",
                        "There is a steady drip under the sink in room 108 and the floor is wet by evening.",
                        "Plumbing", "GH-108", Priority.HIGH, "stu-meera", "tech-fac",
                        Stage.CLOSED, 100, 9, "Replaced the washer and tightened the joint"),

                new Scenario("Monitor flickers in the physics lab",
                        "The monitor on the corner machine flickers every few seconds, which makes it hard to read anything.",
                        "Lab Computer", "SCI-PHY", Priority.MEDIUM, "stu-ritu", "tech-it-1",
                        Stage.CLOSED, 150, 20, "Swapped the display cable"),

                // Deliberately late: this one shows an SLA state of "Missed".
                new Scenario("Tube light flickering in Room 101",
                        "The tube light nearest the window flickers constantly and gives everyone a headache during the first hour.",
                        "Lighting", "A101", Priority.LOW, "stu-zaid", "tech-elec-1",
                        Stage.CLOSED, 220, 96, "Replaced the tube and the starter"),

                // --- came back ---------------------------------------------------
                new Scenario("No power in the socket near the window",
                        "The socket by the window in Room 201 does not work. The others in the room are fine.",
                        "Power Socket", "A201", Priority.MEDIUM, "stu-karan", "tech-elec-2",
                        Stage.REOPENED, 60, 8, "It worked for a day and then stopped again"),

                // --- refused ------------------------------------------------------
                new Scenario("Broken chair in the classroom",
                        "One of the chairs in Room 102 has a loose leg and tips over when you lean back.",
                        "Furniture", "A102", Priority.LOW, "stu-priya", null,
                        Stage.REJECTED, 50, 6, "Already reported by the class representative last week — duplicate")
        );
    }

    private Long create(Scenario scenario,
                        Map<String, Category> categories,
                        Map<String, Location> locations,
                        Map<String, User> people) {
        actAs(people.get(scenario.reporter()));

        RequestDetailResponse created = requestService.create(new CreateRequestRequest(
                scenario.title(),
                scenario.description(),
                categories.get(scenario.category()).getId(),
                locations.get(scenario.location()).getId(),
                scenario.priority()));

        return created.id();
    }

    /** Walks the request to its stage as the people who would really have done it. */
    private void advance(Long id, Scenario scenario, Map<String, User> people) {
        if (scenario.stage() == Stage.OPEN) {
            return;
        }

        User reporter = people.get(scenario.reporter());

        if (scenario.stage() == Stage.REJECTED) {
            actAs(headFor(scenario, people));
            workflowService.perform(id, StatusAction.REJECT, new StatusChangeRequest(scenario.note()));
            return;
        }

        User technician = people.get(scenario.technician());
        actAs(headFor(scenario, people));
        assignmentService.assign(id, new AssignRequest(technician.getId(),
                scenario.stage() == Stage.ASSIGNED ? scenario.note() : null));

        if (scenario.stage() == Stage.ASSIGNED) {
            return;
        }

        actAs(technician);
        workflowService.perform(id, StatusAction.START, null);

        if (scenario.stage() == Stage.IN_PROGRESS) {
            return;
        }

        workflowService.perform(id, StatusAction.RESOLVE, new StatusChangeRequest(scenario.note()));

        if (scenario.stage() == Stage.RESOLVED) {
            return;
        }

        actAs(reporter);
        if (scenario.stage() == Stage.REOPENED) {
            workflowService.perform(id, StatusAction.REOPEN, new StatusChangeRequest(scenario.note()));
        } else {
            workflowService.perform(id, StatusAction.CONFIRM, null);
        }
    }

    /** The head of whichever department owns this request's category. */
    private User headFor(Scenario scenario, Map<String, User> people) {
        return switch (scenario.category()) {
            case "Wi-Fi", "Lab Computer", "Projector", "Printer" -> people.get("head-it");
            case "Fan", "Lighting", "Power Socket" -> people.get("head-elec");
            case "Furniture", "Plumbing", "Cleanliness" -> people.get("head-fac");
            default -> people.get("head-hostel");
        };
    }

    /**
     * Shifts a finished request into the past.
     *
     * <p>Everything was written a fraction of a second ago, so without this the
     * whole list would read "reported just now" and every SLA badge would say
     * "on track". Timeline and assignment rows are moved with it, and then
     * spread across the request's life so the history reads like something that
     * happened over days rather than in one instant.
     */
    private void backdate(Long id, Scenario scenario) {
        int age = scenario.reportedHoursAgo();
        int acted = scenario.actedAfterHours();

        entityManager.createNativeQuery("""
                update service_requests
                   set created_at  = date_sub(utc_timestamp(), interval :age hour),
                       updated_at  = date_sub(utc_timestamp(), interval :age hour),
                       due_at      = date_add(date_sub(utc_timestamp(), interval :age hour),
                                              interval :sla hour),
                       assigned_at = case when assigned_at is null then null
                                     else date_sub(utc_timestamp(), interval :assignedAgo hour) end,
                       resolved_at = case when resolved_at is null then null
                                     else date_sub(utc_timestamp(), interval :actedAgo hour) end,
                       closed_at   = case when closed_at is null then null
                                     else date_sub(utc_timestamp(), interval :closedAgo hour) end
                 where id = :id
                """)
                .setParameter("age", age)
                .setParameter("sla", scenario.priority().getSlaHours())
                .setParameter("assignedAgo", Math.max(age - Math.max(acted / 2, 1), 0))
                .setParameter("actedAgo", Math.max(age - acted, 0))
                .setParameter("closedAgo", Math.max(age - acted - 2, 0))
                .setParameter("id", id)
                .executeUpdate();

        entityManager.createNativeQuery("""
                update assignments
                   set assigned_at = date_sub(utc_timestamp(), interval :assignedAgo hour)
                 where request_id = :id
                """)
                .setParameter("assignedAgo", Math.max(age - Math.max(acted / 2, 1), 0))
                .setParameter("id", id)
                .executeUpdate();

        spreadTimeline(id, age, acted);
    }

    /**
     * Walks the timeline entries in order and lays them out evenly between when
     * the request was reported and when the last thing happened to it.
     */
    @SuppressWarnings("unchecked")
    private void spreadTimeline(Long id, int age, int acted) {
        List<Number> logIds = entityManager.createNativeQuery(
                        "select id from activity_logs where request_id = :id order by id asc")
                .setParameter("id", id)
                .getResultList();

        if (logIds.isEmpty()) {
            return;
        }

        int span = Math.max(acted, 1);
        for (int i = 0; i < logIds.size(); i++) {
            int hoursAgo = age - (int) Math.round((double) span * i / Math.max(logIds.size() - 1, 1));
            entityManager.createNativeQuery(
                            "update activity_logs set created_at = date_sub(utc_timestamp(), interval :ago hour) where id = :logId")
                    .setParameter("ago", Math.max(hoursAgo, 0))
                    .setParameter("logId", logIds.get(i).longValue())
                    .executeUpdate();
        }
    }

    /**
     * Puts a person into the security context so the services see them as the
     * caller. This is the same shape {@code JwtAuthenticationFilter} builds from
     * a token — the services cannot tell the difference, which is the point.
     */
    private void actAs(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getDepartment() == null ? null : user.getDepartment().getId());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
