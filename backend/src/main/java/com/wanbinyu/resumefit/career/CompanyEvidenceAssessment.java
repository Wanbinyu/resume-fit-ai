package com.wanbinyu.resumefit.career;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static com.wanbinyu.resumefit.career.CareerDtos.*;

/** Describes the evidence that exists; it does not endorse claims or infer company reputation. */
public final class CompanyEvidenceAssessment {
    private CompanyEvidenceAssessment() {}

    public static EvidenceAssessment inspect(List<EvidenceInput> evidence) {
        int facts = 0, opinions = 0, unverified = 0, stale = 0;
        var links = new LinkedHashSet<String>();
        var categories = new LinkedHashSet<String>();
        boolean riskNeedsReview = false;
        for (EvidenceInput item : evidence) {
            switch (item.type()) {
                case "FACT" -> facts++;
                case "OPINION" -> opinions++;
                default -> unverified++;
            }
            categories.add(item.category());
            if (!item.sourceUrl().isBlank()) links.add(item.sourceUrl());
            if (item.observedAt().isBefore(LocalDate.now().minusDays(180))) stale++;
            if (item.category().equals("RISK")) riskNeedsReview = true;
        }
        var questions = new ArrayList<String>();
        if (!categories.contains("IDENTITY")) questions.add("核实招聘品牌、劳动合同主体与实际用人单位是否一致。");
        if (!categories.contains("COMPENSATION")) questions.add("向招聘方核实固定薪资、绩效、试用期待遇及发薪安排。");
        if (!categories.contains("WORKLIFE")) questions.add("核实岗位所在团队的工作时间、加班安排和实际工作地点。");
        if (links.size() < 2) questions.add("补充来自不同来源的可核实资料；单条评论不能代表整家公司。");
        if (stale > 0) questions.add("部分资料已超过 180 天，请核实是否仍适用于当前团队与岗位。");
        if (riskNeedsReview) questions.add("存在标记为风险的资料，请核对主体、时间和原始出处，不将未经核实的说法当作结论。");
        String status = riskNeedsReview ? "REVIEW_REQUIRED"
                : links.size() >= 2 && facts >= 2 ? "SOURCES_RECORDED" : "INSUFFICIENT_DATA";
        return new EvidenceAssessment(status, null, facts, opinions, unverified, links.size(), stale,
                List.copyOf(categories), List.copyOf(questions),
                "资料由用户整理。FACT 也是用户标注，系统未验证事实真伪；来源数量不等于信誉。暂不生成公司信誉综合分，也不把未查到负面信息视为可靠。");
    }
}
