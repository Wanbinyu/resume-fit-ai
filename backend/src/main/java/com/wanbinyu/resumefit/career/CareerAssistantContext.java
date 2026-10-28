package com.wanbinyu.resumefit.career;

import java.util.List;
import java.util.Map;
import static com.wanbinyu.resumefit.career.CareerDtos.*;

/** Server-created immutable task data, never accepted directly from HTTP/model input. */
public record CareerAssistantContext(String intent, ProfileView profile, List<JobView> jobs,
                                     List<CompanyView> companies, Map<String,Object> applications) {}
