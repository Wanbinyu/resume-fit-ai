/**
 * 分析任务与历史报告。Controller 接收请求，Service 校验所有权并创建、取消任务，Worker 异步运行 Agent 并持久化结果。
 * 任务状态和工具轨迹属于任务；只有成功分析才生成历史报告，分析本身不保存简历新版本。
 */
package com.wanbinyu.resumefit.analysis;
