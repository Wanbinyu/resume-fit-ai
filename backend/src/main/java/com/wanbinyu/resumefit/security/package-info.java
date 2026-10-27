/**
 * 无状态 JWT 鉴权。过滤器验证令牌后将用户 ID 和角色写入安全上下文，SecurityConfig 控制公开、登录及管理员接口。
 * 接口鉴权不能代替数据所有权校验；简历、JD 和任务的归属仍由业务 Service 检查。
 */
package com.wanbinyu.resumefit.security;
