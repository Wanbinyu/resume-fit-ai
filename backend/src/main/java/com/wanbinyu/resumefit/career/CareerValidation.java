package com.wanbinyu.resumefit.career;

import com.wanbinyu.resumefit.common.BusinessException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Component
public class CareerValidation {
    private final Validator validator;

    public CareerValidation(Validator validator) { this.validator = validator; }

    public void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new BusinessException("字段不完整、类型不正确或超过长度限制");
        }
    }

    public String text(String value) { return value == null ? "" : value.strip(); }

    public List<String> strings(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::text).distinct().toList();
    }

    public void salary(Integer min, Integer max) {
        if (min != null && max != null && min > max) throw new BusinessException("薪资下限不能高于上限");
    }

    /** These URLs are stored for people to open; the backend never fetches them. */
    public String sourceUrl(String value) {
        String url = text(value);
        if (url.isEmpty()) return "";
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()
                    || uri.getRawUserInfo() != null || host.equalsIgnoreCase("localhost")
                    || host.contains(":") || host.matches("[0-9.]+")
                    || !host.contains(".") || url.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("来源链接需为不含账号密码的公开 HTTPS 地址");
        }
    }

    public LocalDate observedDate(LocalDate value) {
        LocalDate date = value == null ? LocalDate.now() : value;
        if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new BusinessException("信息日期需在 1900 年至今天之间");
        }
        return date;
    }
}
