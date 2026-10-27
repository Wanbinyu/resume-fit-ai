/**
 * 简历、版本、分享与文档导出。ResumeEntity 保存初始简历，ResumeVersionEntity 保存可编辑或发布的各个版本。
 * Service 负责所有权校验和业务规则，Mapper 访问数据库，DTO 定义接口数据；导出只读取用户已保存的版本。
 */
package com.wanbinyu.resumefit.resume;
