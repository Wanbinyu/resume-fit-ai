package com.wanbinyu.resumefit.resume;

import com.wanbinyu.resumefit.common.BusinessException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class FileTextExtractor {
    private final Tika tika = new Tika();

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请上传简历文件");
        if (file.getSize() > 5 * 1024 * 1024) throw new BusinessException("文件不能超过 5MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt"))) {
            throw new BusinessException("仅支持 PDF、DOC、DOCX 或 TXT 文件");
        }
        try {
            String text = tika.parseToString(file.getInputStream()).replaceAll("\\s+", " ").trim();
            if (text.isBlank()) throw new BusinessException("无法从文件中提取文字");
            return text.length() > 6000 ? text.substring(0, 6000) : text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("简历文件读取失败");
        } catch (Exception exception) {
            throw new BusinessException("简历文件解析失败");
        }
    }
}
