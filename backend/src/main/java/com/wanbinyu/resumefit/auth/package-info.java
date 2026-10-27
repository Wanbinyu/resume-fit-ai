/**
 * 账号注册和登录。密码经 PasswordEncoder 哈希后保存，登录成功后由 JwtService 签发访问令牌。
 * 本包不处理后续请求的令牌解析；解析与接口访问规则位于 security 包。
 */
package com.wanbinyu.resumefit.auth;
