import assert from "node:assert/strict";
import test from "node:test";
import { findCandidateProfileConflict } from "../profile.js";

test("blocks experienced claims when the selected identity is fresh", () => {
  assert.match(
    findCandidateProfileConflict("Java 后端工程师，拥有 3 年开发经验。", "fresh"),
    /选择了应届生/
  );
  assert.match(
    findCandidateProfileConflict("工作经验：2年", "fresh"),
    /选择了应届生/
  );
});

test("allows internships and ordinary education durations for fresh candidates", () => {
  assert.equal(findCandidateProfileConflict("应届生，有 1 年实习经验，本科四年。", "fresh"), "");
  assert.equal(findCandidateProfileConflict("参与为期 3 年的校园项目。", "fresh"), "");
});

test("blocks explicitly fresh resumes when social hiring is selected", () => {
  assert.match(findCandidateProfileConflict("2026 届应届毕业生，暂无工作经验。", "experienced"), /选择了社招/);
  assert.equal(findCandidateProfileConflict("2022 届毕业生，3 年工作经验。", "experienced"), "");
});
