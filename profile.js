export function findCandidateProfileConflict(resume, candidateType) {
  const text = String(resume || "").normalize("NFKC");
  const years = extractExperienceYears(text);
  const maxYears = years.length ? Math.max(...years) : 0;

  if (candidateType === "fresh" && maxYears >= 1) {
    return `你选择了应届生，但简历中出现“${formatYears(maxYears)}年经验”。请将求职身份改为社招并选择年限，或修正简历中的经验描述。`;
  }

  const explicitlyFresh = /(?:应届(?:生|毕业生)|20\d{2}\s*届(?:毕业生)?|暂无(?:正式)?工作经验|无(?:正式)?工作经验|零工作经验|0\s*年(?:工作)?经验)/i.test(text);
  if (candidateType === "experienced" && explicitlyFresh && maxYears < 1) {
    return "你选择了社招，但简历中明确写有应届生或无工作经验。请改为应届生，或修正简历中的身份描述。";
  }

  return "";
}

function extractExperienceYears(text) {
  const values = [];
  const patterns = [
    /(?:拥有|具备|累计|已有|约|超过)?\s*(\d+(?:\.\d+)?)\s*年(?:以上)?(?:的)?(?:全职|正式|工作|从业|行业|开发|设计|运营|销售|相关)?经验/gi,
    /(?:全职|正式|工作|从业|行业|开发|设计|运营|销售|相关)?经验\s*(?:为|有|约|：|:)?\s*(\d+(?:\.\d+)?)\s*年/gi
  ];

  patterns.forEach((pattern) => {
    for (const match of text.matchAll(pattern)) {
      const value = Number(match[1]);
      if (Number.isFinite(value)) values.push(value);
    }
  });
  return values;
}

function formatYears(value) {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(1)));
}
