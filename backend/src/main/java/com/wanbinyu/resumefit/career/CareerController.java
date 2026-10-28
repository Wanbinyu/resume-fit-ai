package com.wanbinyu.resumefit.career;

import com.wanbinyu.resumefit.common.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.wanbinyu.resumefit.career.CareerDtos.*;

@RestController
@RequestMapping("/api/career")
public class CareerController {
    private final CareerService service;
    public CareerController(CareerService service) { this.service=service; }
    @GetMapping("/profile")
    public ApiResponse<ProfileView> profile(Authentication auth) {
        return ApiResponse.ok(service.profile(CurrentUser.id(auth)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileView> profile(Authentication auth,@Valid @RequestBody ProfileInput input) {
        return ApiResponse.ok(service.saveProfile(CurrentUser.id(auth),input));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobView>> jobs(Authentication auth,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="false") boolean includeArchived) {
        return ApiResponse.ok(service.listJobs(CurrentUser.id(auth),limit,includeArchived));
    }

    @PostMapping("/jobs")
    public ApiResponse<JobView> createJob(Authentication auth,@Valid @RequestBody JobInput input) {
        return ApiResponse.ok(service.createJob(CurrentUser.id(auth),input));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<JobView> job(Authentication auth,@PathVariable long id) {
        return ApiResponse.ok(service.job(CurrentUser.id(auth),id));
    }

    @PatchMapping("/jobs/{id}")
    public ApiResponse<JobView> patchJob(Authentication auth,@PathVariable long id,@Valid @RequestBody JobPatch input) {
        return ApiResponse.ok(service.updateJobStatus(CurrentUser.id(auth),id,input));
    }

    @GetMapping("/companies")
    public ApiResponse<List<CompanyView>> companies(Authentication auth,@RequestParam(defaultValue="50") int limit) {
        return ApiResponse.ok(service.listCompanies(CurrentUser.id(auth),limit));
    }

    @PostMapping("/companies")
    public ApiResponse<CompanyView> createCompany(Authentication auth,@Valid @RequestBody CompanyInput input) {
        return ApiResponse.ok(service.createCompany(CurrentUser.id(auth),input));
    }

    @GetMapping("/companies/{id}")
    public ApiResponse<CompanyView> company(Authentication auth,@PathVariable long id) {
        return ApiResponse.ok(service.company(CurrentUser.id(auth),id));
    }

    @PutMapping("/companies/{id}")
    public ApiResponse<CompanyView> updateCompany(Authentication auth,@PathVariable long id,@Valid @RequestBody CompanyInput input) {
        return ApiResponse.ok(service.updateCompany(CurrentUser.id(auth),id,input));
    }

    @GetMapping("/applications")
    public ApiResponse<List<ApplicationView>> applications(Authentication auth,@RequestParam(defaultValue="50") int limit) {
        return ApiResponse.ok(service.listApplications(CurrentUser.id(auth),limit));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<ApplicationView> application(Authentication auth,@PathVariable long id) {
        return ApiResponse.ok(service.application(CurrentUser.id(auth),id));
    }

    @PostMapping("/applications")
    public ApiResponse<ApplicationView> createApplication(Authentication auth,@Valid @RequestBody ApplicationInput input) {
        return ApiResponse.ok(service.createApplication(CurrentUser.id(auth),input));
    }

    @PatchMapping("/applications/{id}")
    public ApiResponse<ApplicationView> patchApplication(Authentication auth,@PathVariable long id,@Valid @RequestBody ApplicationPatch input) {
        return ApiResponse.ok(service.updateApplication(CurrentUser.id(auth),id,input));
    }

    @GetMapping("/applications/{id}/events")
    public ApiResponse<List<ApplicationEventView>> events(Authentication auth,@PathVariable long id,@RequestParam(defaultValue="50") int limit) {
        return ApiResponse.ok(service.applicationEvents(CurrentUser.id(auth),id,limit));
    }

}
