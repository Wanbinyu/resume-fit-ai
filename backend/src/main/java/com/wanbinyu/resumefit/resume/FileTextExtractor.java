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
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt"))) {
            throw new BusinessException("仅支持 PDF、DOC、DOCX 或 TXT 文件");
        }
        try {
            String text;
            try (var stream = file.getInputStream()) {
                text = tika.parseToString(stream).replace("\r\n", "\n").replace('\r', '\n')
                        .replaceAll("[\\t\\x0B\\f ]+", " ").replaceAll("\\n *", "\n")
                        .replaceAll("\\n{3,}", "\n\n").trim();
            }
            if (text.isBlank()) throw new BusinessException("未识别到可读取的文字；扫描版 PDF 请先进行文字识别，或粘贴简历内容");
            if (text.length() > 6000) throw new BusinessException("识别内容超过 6000 字符，请精简文件或使用手动粘贴；系统没有截断或保存文件内容");
            return text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("简历文件读取失败");
        } catch (Exception exception) {
            throw new BusinessException("简历文件解析失败");
        }
    }
}
