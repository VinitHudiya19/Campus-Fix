package com.campusfix.demo;

import com.campusfix.category.Category;
import com.campusfix.category.CategoryRepository;
import com.campusfix.department.Department;
import com.campusfix.department.DepartmentRepository;
import com.campusfix.location.Location;
import com.campusfix.location.LocationRepository;
import com.campusfix.request.ServiceRequestRepository;
import com.campusfix.user.Role;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fills an empty database with a realistic college so the application can be
 * demonstrated without twenty minutes of clicking first.
 *
 * <p>Runs only under the {@code demo} profile, and only when there are no
 * requests yet — so it cannot overwrite real data, and restarting a demo does
 * not pile up duplicates.
 *
 * <p>Departments, categories, locations and people are written straight through
 * the repositories. The <em>requests</em> are not: {@link DemoRequests} drives
 * them through the real services so that every seeded request has a genuine
 * history behind it and could not be in a state the workflow forbids.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** One password for every seeded account, so a demo is not a memory test. */
    static final String DEMO_PASSWORD = "demo1234";

    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final ServiceRequestRepository requestRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoRequests demoRequests;

    public DemoDataSeeder(DepartmentRepository departmentRepository,
                          CategoryRepository categoryRepository,
                          LocationRepository locationRepository,
                          UserRepository userRepository,
                          ServiceRequestRepository requestRepository,
                          PasswordEncoder passwordEncoder,
                          DemoRequests demoRequests) {
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoRequests = demoRequests;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (requestRepository.count() > 0) {
            log.info("Demo profile is active but the database already has requests — leaving it alone.");
            return;
        }

        Map<String, Department> departments = seedDepartments();
        Map<String, Category> categories = seedCategories(departments);
        Map<String, Location> locations = seedLocations();
        Map<String, User> people = seedPeople(departments);

        demoRequests.seed(categories, locations, people);

        log.warn("""

                ==================================================================
                 Demo data loaded. Every account below uses the password: {}

                   admin@campusfix.local  ....  administrator (password: admin12345)
                   neha.rao@college.edu   ....  head of IT Support
                   amit.sharma@college.edu ...  technician, IT Support
                   priya.nair@college.edu ....  student
                ==================================================================
                """, DEMO_PASSWORD);
    }

    private Map<String, Department> seedDepartments() {
        Map<String, Department> saved = new LinkedHashMap<>();
        record Row(String name, String description) { }

        for (Row row : new Row[]{
                new Row("IT Support", "Network, Wi-Fi, lab computers and projectors"),
                new Row("Electrical", "Wiring, fans, lighting and power points"),
                new Row("Facilities", "Furniture, plumbing and cleaning"),
                new Row("Hostel Maintenance", "Repairs inside the hostel blocks")}) {
            saved.put(row.name(), departmentRepository.save(new Department(row.name(), row.description())));
        }
        return saved;
    }

    private Map<String, Category> seedCategories(Map<String, Department> departments) {
        Map<String, Category> saved = new LinkedHashMap<>();
        record Row(String name, String department, String description) { }

        for (Row row : new Row[]{
                new Row("Wi-Fi", "IT Support", "Wireless network problems"),
                new Row("Lab Computer", "IT Support", "Desktop machines in the labs"),
                new Row("Projector", "IT Support", "Classroom projectors and screens"),
                new Row("Printer", "IT Support", "Shared printers and scanners"),
                new Row("Fan", "Electrical", "Ceiling and wall fans"),
                new Row("Lighting", "Electrical", "Tube lights and bulbs"),
                new Row("Power Socket", "Electrical", "Sockets and switchboards"),
                new Row("Furniture", "Facilities", "Desks, chairs and benches"),
                new Row("Plumbing", "Facilities", "Taps, sinks and drainage"),
                new Row("Cleanliness", "Facilities", "Rooms not cleaned"),
                new Row("Room Repair", "Hostel Maintenance", "Doors, windows and locks"),
                new Row("Water Supply", "Hostel Maintenance", "Water not reaching the block")}) {
            saved.put(row.name(), categoryRepository.save(
                    new Category(row.name(), row.description(), departments.get(row.department()))));
        }
        return saved;
    }

    private Map<String, Location> seedLocations() {
        Map<String, Location> saved = new LinkedHashMap<>();
        record Row(String key, String campus, String building, String floor, String room) { }

        for (Row row : new Row[]{
                new Row("A101", "Main Campus", "Academic Block A", "Floor 1", "Room 101"),
                new Row("A102", "Main Campus", "Academic Block A", "Floor 1", "Room 102"),
                new Row("A201", "Main Campus", "Academic Block A", "Floor 2", "Room 201"),
                new Row("LIB-READ", "Main Campus", "Library Block", "Floor 2", "Reading Hall"),
                new Row("LIB-DESK", "Main Campus", "Library Block", "Ground Floor", "Circulation Desk"),
                new Row("SCI-PHY", "Main Campus", "Science Block", "Floor 3", "Physics Lab"),
                new Row("ADMIN", "Main Campus", "Admin Block", "Ground Floor", "Reception"),
                // No floor and no room: the rule that both are optional, in use.
                new Row("GROUND", "Main Campus", "Sports Ground", null, null),
                new Row("BH-214", "Hostel Campus", "Boys Hostel A", "Floor 2", "Room 214"),
                new Row("BH-COMMON", "Hostel Campus", "Boys Hostel A", "Ground Floor", "Common Room"),
                new Row("GH-108", "Hostel Campus", "Girls Hostel B", "Floor 1", "Room 108")}) {
            saved.put(row.key(), locationRepository.save(
                    new Location(row.campus(), row.building(), row.floor(), row.room())));
        }
        return saved;
    }

    private Map<String, User> seedPeople(Map<String, Department> departments) {
        Map<String, User> saved = new LinkedHashMap<>();
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        record Row(String key, String name, String email, Role role, String department) { }

        for (Row row : new Row[]{
                new Row("head-it", "Neha Rao", "neha.rao@college.edu", Role.DEPARTMENT_HEAD, "IT Support"),
                new Row("head-elec", "Vikram Das", "vikram.das@college.edu", Role.DEPARTMENT_HEAD, "Electrical"),
                new Row("head-fac", "Anita Joshi", "anita.joshi@college.edu", Role.DEPARTMENT_HEAD, "Facilities"),
                new Row("head-hostel", "Rakesh Pillai", "rakesh.pillai@college.edu", Role.DEPARTMENT_HEAD, "Hostel Maintenance"),

                new Row("tech-it-1", "Amit Sharma", "amit.sharma@college.edu", Role.TECHNICIAN, "IT Support"),
                new Row("tech-it-2", "Sana Iqbal", "sana.iqbal@college.edu", Role.TECHNICIAN, "IT Support"),
                new Row("tech-elec-1", "Deepak Verma", "deepak.verma@college.edu", Role.TECHNICIAN, "Electrical"),
                new Row("tech-elec-2", "Farhan Qureshi", "farhan.qureshi@college.edu", Role.TECHNICIAN, "Electrical"),
                new Row("tech-fac", "Suresh Nair", "suresh.nair@college.edu", Role.TECHNICIAN, "Facilities"),
                new Row("tech-hostel", "Manoj Kale", "manoj.kale@college.edu", Role.TECHNICIAN, "Hostel Maintenance"),

                new Row("stu-priya", "Priya Nair", "priya.nair@college.edu", Role.STUDENT, null),
                new Row("stu-karan", "Karan Mehta", "karan.mehta@college.edu", Role.STUDENT, null),
                new Row("stu-ritu", "Ritu Singh", "ritu.singh@college.edu", Role.STUDENT, null),
                new Row("stu-arjun", "Arjun Das", "arjun.das@college.edu", Role.STUDENT, null),
                new Row("stu-meera", "Meera Iyer", "meera.iyer@college.edu", Role.STUDENT, null),
                new Row("stu-zaid", "Zaid Khan", "zaid.khan@college.edu", Role.STUDENT, null)}) {

            saved.put(row.key(), userRepository.save(new User(
                    row.name(), row.email(), hash, row.role(),
                    row.department() == null ? null : departments.get(row.department()))));
        }
        return saved;
    }
}
