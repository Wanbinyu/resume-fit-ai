/**
 * 用户保存的岗位 JD。Controller 接收请求，Service 检查归属并提供 JD，Mapper 负责持久化。
 * 已保存的用户 JD 与 ai 包中的外部搜索摘要是不同来源，不能将摘要默认视为完整或仍在招的职位。
 */
package com.wanbinyu.resumefit.job;
