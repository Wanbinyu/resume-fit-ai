package com.wanbinyu.resumefit.analysis;

import com.wanbinyu.resumefit.career.CareerAssistantContext;

/** Frozen at submission. Tools never silently switch to a subsequently edited version. */
public record AnalysisInputSnapshot(Long versionId, String resumeContent, String jobContent,
                                    CareerAssistantContext assistant) {}
