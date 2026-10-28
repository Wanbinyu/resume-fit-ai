package com.wanbinyu.resumefit.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanbinyu.resumefit.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.wanbinyu.resumefit.career.CareerDtos.*;

/** Account-bound business operations. Agent integrations may call the read methods only. */
@Service
public class CareerService {
    public static final Set<String> APPLICATION_STAGES = Set.of("SAVED", "APPLIED", "CONTACTED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
    private static final Set<String> CLOSED_STAGES = Set.of("REJECTED", "WITHDRAWN");
    private static final TypeReference<List<EvidenceInput>> EVIDENCE_TYPE = new TypeReference<>() {};
    private final CareerRepository repository;
    private final CareerValidation validation;
    private final ObjectMapper json;

    public CareerService(CareerRepository repository, CareerValidation validation, ObjectMapper json) {
        this.repository = repository;
        this.validation = validation;
        this.json = json;
    }

    public ProfileView profile(long userId) {
        return repository.profile(userId).map(row -> {
            ProfileInput value = decode(row.contentJson(), ProfileInput.class);
            return new ProfileView(value.targetRoles(), value.preferredCities(), value.salaryMin(), value.salaryMax(),
                    Boolean.TRUE.equals(value.remoteAccepted()), value.priorities(), value.notes(), row.revision(), row.updatedAt());
        }).orElseGet(() -> new ProfileView(List.of(), List.of(), null, null, false, List.of(), "", 0, null));
    }

    @Transactional
    public ProfileView saveProfile(long userId, ProfileInput request) {
        validation.validate(request);
        validation.salary(request.salaryMin(), request.salaryMax());
        ProfileInput value = new ProfileInput(validation.strings(request.targetRoles()), validation.strings(request.preferredCities()),
                request.salaryMin(), request.salaryMax(), Boolean.TRUE.equals(request.remoteAccepted()),
                validation.strings(request.priorities()), validation.text(request.notes()), revision(request.revision()));
        repository.lockAccount(userId);
        var previous = repository.profile(userId);
        if (previous.isEmpty()) {
            if (revision(request.revision()) != 0) throw conflict();
            repository.createProfile(userId, encode(value));
        } else if (!repository.updateProfile(userId, revision(request.revision()), encode(value))) {
            throw conflict();
        }
        return profile(userId);
    }

    public List<JobView> listJobs(long userId, int limit) { return listJobs(userId, limit, false); }

    public List<JobView> listJobs(long userId, int limit, boolean includeArchived) {
        return repository.jobs(userId, boundedLimit(limit), includeArchived);
    }

    public JobView job(long userId, long id) {
        return repository.job(userId, id).orElseThrow(() -> new BusinessException("岗位不存在或无访问权限"));
    }

    @Transactional
    public JobView createJob(long userId, JobInput request) {
        validation.validate(request);
        validation.salary(request.salaryMin(), request.salaryMax());
        LocalDate collected = validation.observedDate(request.collectedAt());
        if (request.expiresAt() != null && (request.expiresAt().isBefore(collected)
                || request.expiresAt().isAfter(LocalDate.now().plusYears(5)))) {
            throw new BusinessException("失效日期不能早于收集日期，且不能超过未来五年");
        }
        String companyName = validation.text(request.companyName());
        String source = validation.sourceUrl(request.sourceUrl());
        repository.lockAccount(userId);
        if (request.companyId() != null && !company(userId, request.companyId()).name().equals(companyName)) {
            throw new BusinessException("岗位公司名称与所关联的公司档案不一致");
        }
        quota(userId, "career_jobs", 500);
        var value = new JobInput(validation.text(request.title()), companyName, request.companyId(), validation.text(request.location()),
                request.salaryMin(), request.salaryMax(), validation.text(request.description()), source, collected, request.expiresAt());
        return job(userId, repository.createJob(userId, value));
    }

    @Transactional
    public JobView updateJobStatus(long userId, long id, JobPatch request) {
        validation.validate(request);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(request.status())) throw new BusinessException("岗位状态只能为 ACTIVE 或 ARCHIVED");
        repository.lockAccount(userId);
        job(userId, id);
        if (!repository.updateJobStatus(userId, id, request.revision(), request.status())) throw conflict();
        return job(userId, id);
    }

    public List<CompanyView> listCompanies(long userId, int limit) {
        return repository.companies(userId, boundedLimit(limit)).stream().map(this::companyView).toList();
    }

    public CompanyView company(long userId, long id) {
        return companyView(repository.company(userId, id).orElseThrow(() -> new BusinessException("公司档案不存在或无访问权限")));
    }

    @Transactional
    public CompanyView createCompany(long userId, CompanyInput request) {
        CompanyInput value = normalizedCompany(request);
        if (revision(value.revision()) != 0) throw conflict();
        repository.lockAccount(userId);
        quota(userId, "career_companies", 200);
        return company(userId, repository.createCompany(userId, value, encode(value.evidence())));
    }

    @Transactional
    public CompanyView updateCompany(long userId, long id, CompanyInput request) {
        CompanyInput value = normalizedCompany(request);
        repository.lockAccount(userId);
        company(userId, id);
        if (!repository.updateCompany(userId, id, revision(value.revision()), value, encode(value.evidence()))) throw conflict();
        repository.syncCompanyName(userId, id, value.name());
        return company(userId, id);
    }

    /** A read-only tool can request this without access to another user's company. */
    public EvidenceAssessment assessCompany(long userId, long id) { return company(userId, id).evidenceAssessment(); }

    public List<ApplicationView> listApplications(long userId, int limit) {
        return repository.applications(userId, boundedLimit(limit));
    }

    public ApplicationView application(long userId, long id) {
        return repository.application(userId, id).orElseThrow(() -> new BusinessException("投递记录不存在或无访问权限"));
    }

    @Transactional
    public ApplicationView createApplication(long userId, ApplicationInput request) {
        validation.validate(request);
        stage(request.stage());
        nextAction(request.nextActionAt());
        repository.lockAccount(userId);
        JobView selected = job(userId, request.jobId());
        // Same job is an idempotency key: retries return the saved record and never overwrite progress.
        var existing = repository.applicationForJob(userId, request.jobId());
        if (existing.isPresent()) return existing.get();
        if (!selected.status().equals("ACTIVE")) throw new BusinessException("请先恢复该岗位，再创建投递记录");
        quota(userId, "career_applications", 500);
        long id = repository.createApplication(userId, new ApplicationInput(request.jobId(), request.stage(),
                validation.text(request.notes()), request.nextActionAt()));
        repository.applicationEvent(userId, id, null, request.stage(), "CREATED");
        return application(userId, id);
    }

    @Transactional
    public ApplicationView updateApplication(long userId, long id, ApplicationPatch request) {
        validation.validate(request);
        stage(request.stage());
        nextAction(request.nextActionAt());
        repository.lockAccount(userId);
        ApplicationView previous = application(userId, id);
        ApplicationPatch value = new ApplicationPatch(request.stage(), validation.text(request.notes()), request.nextActionAt(), request.revision());
        if (!repository.updateApplication(userId, id, value)) throw conflict();
        // Users may correct stages, including reopening a closed application. Every change is traceable.
        String action = previous.stage().equals(request.stage()) ? "DETAILS_UPDATED"
                : CLOSED_STAGES.contains(previous.stage()) ? "STAGE_CORRECTED" : "STAGE_CHANGED";
        repository.applicationEvent(userId, id, previous.stage(), request.stage(), action);
        return application(userId, id);
    }

    public List<ApplicationEventView> applicationEvents(long userId, long id, int limit) {
        application(userId, id);
        return repository.applicationEvents(userId, id, boundedLimit(limit));
    }

    public Map<String, Object> summarizeApplications(long userId) {
        List<ApplicationView> records = repository.applications(userId, 500);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String stage : List.of("SAVED", "APPLIED", "CONTACTED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN")) {
            counts.put(stage, records.stream().filter(record -> record.stage().equals(stage)).count());
        }
        var overdue = records.stream().filter(record -> !CLOSED_STAGES.contains(record.stage())
                && record.nextActionAt() != null && record.nextActionAt().isBefore(LocalDateTime.now())).limit(10).toList();
        var summaries=overdue.stream().map(record -> Map.of("id",record.id(),"jobId",record.jobId(),"jobTitle",record.jobTitle(),
                "companyName",record.companyName(),"stage",record.stage(),"nextActionAt",record.nextActionAt())).toList();
        return Map.of("total", records.size(), "stageCounts", counts, "overdue", summaries,
                "notice", "这是用户保存的跟进记录；系统不会自动投递、发送消息或安排提醒。");
    }

    private CompanyInput normalizedCompany(CompanyInput request) {
        validation.validate(request);
        List<EvidenceInput> evidence = request.evidence() == null ? List.of() : request.evidence().stream().map(item -> {
            if (item == null) throw new BusinessException("公司证据不能包含空项");
            validation.validate(item);
            return new EvidenceInput(item.type(), item.category(), validation.text(item.title()), validation.text(item.content()),
                    validation.sourceUrl(item.sourceUrl()), validation.observedDate(item.observedAt()));
        }).toList();
        return new CompanyInput(validation.text(request.name()), validation.text(request.industry()), validation.sourceUrl(request.website()), evidence, request.revision());
    }

    private CompanyView companyView(CareerRepository.CompanyRow row) {
        List<EvidenceInput> evidence;
        try { evidence = json.readValue(row.evidenceJson(), EVIDENCE_TYPE); }
        catch (Exception exception) { throw new BusinessException("公司证据暂时无法读取，请稍后重试"); }
        return new CompanyView(row.id(), row.name(), row.industry(), row.website(), evidence,
                CompanyEvidenceAssessment.inspect(evidence), row.revision(), row.createdAt(), row.updatedAt());
    }

    private void quota(long userId, String table, int maximum) {
        if (repository.count(userId, table) >= maximum) throw new BusinessException("已达到当前账号的记录上限（" + maximum + " 条）");
    }
    private void stage(String value) {
        if (!APPLICATION_STAGES.contains(value)) throw new BusinessException("投递状态无效");
    }
    private void nextAction(LocalDateTime value) {
        if (value != null && (value.isBefore(LocalDateTime.of(1900, 1, 1, 0, 0)) || value.isAfter(LocalDateTime.now().plusYears(5)))) {
            throw new BusinessException("跟进时间超出支持范围");
        }
    }
    private static long revision(Long revision) { return revision == null ? 0 : revision; }
    private static int boundedLimit(int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException("每次读取条数需在 1 至 100 之间");
        return limit;
    }
    private static BusinessException conflict() { return new BusinessException("内容已被更新，请刷新后重新操作"); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Career data cannot be serialized", exception); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception exception) { throw new BusinessException("求职资料暂时无法读取，请稍后重试"); }
    }
}
