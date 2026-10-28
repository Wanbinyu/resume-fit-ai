package com.wanbinyu.resumefit.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.wanbinyu.resumefit.career.CareerDtos.*;

/** Every account-scoped query includes user_id, including lookups by primary key. */
@Repository
public class CareerRepository {
    private final JdbcTemplate jdbc;
    private static final Set<String> TABLES = Set.of("career_jobs", "career_companies", "career_applications");

    public CareerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record ProfileRow(String contentJson, long revision, LocalDateTime updatedAt) {}
    record CompanyRow(long id, String name, String industry, String website, String evidenceJson,
                      long revision, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    private static final RowMapper<CompanyRow> COMPANY = (rs, index) -> new CompanyRow(
            rs.getLong("id"), rs.getString("name"), rs.getString("industry"), rs.getString("website"),
            rs.getString("evidence_json"), rs.getLong("revision"), time(rs, "created_at"), time(rs, "updated_at"));

    private static final RowMapper<JobView> JOB = (rs, index) -> new JobView(
            rs.getLong("id"), rs.getString("title"), rs.getString("company_name"), number(rs, "company_id"),
            rs.getString("location"), integer(rs, "salary_min"), integer(rs, "salary_max"), rs.getString("description"),
            rs.getString("source_url"), date(rs, "collected_at"), date(rs, "expires_at"), rs.getString("status"),
            rs.getLong("revision"), time(rs, "created_at"), time(rs, "updated_at"));

    private static final RowMapper<ApplicationView> APPLICATION = (rs, index) -> new ApplicationView(
            rs.getLong("id"), rs.getLong("job_id"), rs.getString("job_title"), rs.getString("company_name"),
            rs.getString("stage"), rs.getString("notes"), time(rs, "next_action_at"), rs.getLong("revision"),
            time(rs, "created_at"), time(rs, "updated_at"));

    private static final String APPLICATION_SELECT = """
            SELECT a.*, j.title AS job_title, j.company_name FROM career_applications a
            JOIN career_jobs j ON j.id = a.job_id AND j.user_id = a.user_id
            """;

    /** Serializes short, local writes per account: quota check + insert are atomic. No remote I/O inside. */
    public void lockAccount(long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }

    public int count(long userId, String table) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported career table");
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return value == null ? 0 : value;
    }

    public Optional<ProfileRow> profile(long userId) {
        return first(jdbc.query("SELECT * FROM career_profiles WHERE user_id = ?", (rs, index) ->
                new ProfileRow(rs.getString("content_json"), rs.getLong("revision"), time(rs, "updated_at")), userId));
    }

    public void createProfile(long userId, String json) {
        jdbc.update("INSERT INTO career_profiles (user_id, content_json, revision) VALUES (?, ?, 1)", userId, json);
    }

    public boolean updateProfile(long userId, long revision, String json) {
        return jdbc.update("UPDATE career_profiles SET content_json = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revision = ?",
                json, userId, revision) == 1;
    }

    public Optional<JobView> job(long userId, long id) {
        return first(jdbc.query("SELECT * FROM career_jobs WHERE id = ? AND user_id = ?", JOB, id, userId));
    }

    public List<JobView> jobs(long userId, int limit, boolean includeArchived) {
        String condition = includeArchived ? "" : " AND status = 'ACTIVE'";
        return jdbc.query("SELECT * FROM career_jobs WHERE user_id = ?" + condition + " ORDER BY updated_at DESC, id DESC LIMIT ?", JOB, userId, limit);
    }

    public long createJob(long userId, JobInput input) {
        return insert("""
                INSERT INTO career_jobs (user_id, title, company_name, company_id, location, salary_min, salary_max,
                                         description, source_url, collected_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, input.title(), input.companyName(), input.companyId(), input.location(), input.salaryMin(),
                input.salaryMax(), input.description(), input.sourceUrl(), input.collectedAt(), input.expiresAt());
    }

    public boolean updateJobStatus(long userId, long id, long revision, String status) {
        return jdbc.update("UPDATE career_jobs SET status = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND revision = ?",
                status, id, userId, revision) == 1;
    }

    public Optional<CompanyRow> company(long userId, long id) {
        return first(jdbc.query("SELECT * FROM career_companies WHERE id = ? AND user_id = ?", COMPANY, id, userId));
    }

    public List<CompanyRow> companies(long userId, int limit) {
        return jdbc.query("SELECT * FROM career_companies WHERE user_id = ? ORDER BY updated_at DESC, id DESC LIMIT ?", COMPANY, userId, limit);
    }

    public long createCompany(long userId, CompanyInput input, String evidence) {
        return insert("INSERT INTO career_companies (user_id, name, industry, website, evidence_json) VALUES (?, ?, ?, ?, ?)",
                userId, input.name(), input.industry(), input.website(), evidence);
    }

    public boolean updateCompany(long userId, long id, long revision, CompanyInput input, String evidence) {
        return jdbc.update("UPDATE career_companies SET name = ?, industry = ?, website = ?, evidence_json = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND revision = ?",
                input.name(), input.industry(), input.website(), evidence, id, userId, revision) == 1;
    }

    public void syncCompanyName(long userId, long companyId, String name) {
        jdbc.update("UPDATE career_jobs SET company_name = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE company_id = ? AND user_id = ? AND company_name <> ?",
                name, companyId, userId, name);
    }

    public Optional<ApplicationView> application(long userId, long id) {
        return first(jdbc.query(APPLICATION_SELECT + " WHERE a.id = ? AND a.user_id = ?", APPLICATION, id, userId));
    }

    public Optional<ApplicationView> applicationForJob(long userId, long jobId) {
        return first(jdbc.query(APPLICATION_SELECT + " WHERE a.job_id = ? AND a.user_id = ?", APPLICATION, jobId, userId));
    }

    public List<ApplicationView> applications(long userId, int limit) {
        return jdbc.query(APPLICATION_SELECT + " WHERE a.user_id = ? ORDER BY a.updated_at DESC, a.id DESC LIMIT ?", APPLICATION, userId, limit);
    }

    public long createApplication(long userId, ApplicationInput input) {
        return insert("INSERT INTO career_applications (user_id, job_id, stage, notes, next_action_at) VALUES (?, ?, ?, ?, ?)",
                userId, input.jobId(), input.stage(), input.notes(), input.nextActionAt());
    }

    public boolean updateApplication(long userId, long id, ApplicationPatch input) {
        return jdbc.update("UPDATE career_applications SET stage = ?, notes = ?, next_action_at = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND revision = ?",
                input.stage(), input.notes(), input.nextActionAt(), id, userId, input.revision()) == 1;
    }

    public void applicationEvent(long userId, long applicationId, String from, String to, String action) {
        jdbc.update("INSERT INTO career_application_events (user_id, application_id, from_stage, to_stage, action) VALUES (?, ?, ?, ?, ?)",
                userId, applicationId, from, to, action);
    }

    public List<ApplicationEventView> applicationEvents(long userId, long applicationId, int limit) {
        return jdbc.query("SELECT * FROM career_application_events WHERE user_id = ? AND application_id = ? ORDER BY id DESC LIMIT ?",
                (rs, index) -> new ApplicationEventView(rs.getLong("id"), rs.getLong("application_id"),
                        rs.getString("from_stage"), rs.getString("to_stage"), rs.getString("action"), time(rs, "occurred_at")), userId, applicationId, limit);
    }

    private long insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, holder);
        Number id = holder.getKey();
        if (id == null) throw new IllegalStateException("Career insert did not return an id");
        return id.longValue();
    }

    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime();
    }
    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column); return value == null ? null : value.toLocalDate();
    }
    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column); return value == null ? null : value.intValue();
    }
    private static Long number(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column); return value == null ? null : value.longValue();
    }
}
