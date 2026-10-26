package com.wanbinyu.resumefit;

import com.wanbinyu.resumefit.resume.FileTextExtractor;
import com.wanbinyu.resumefit.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ResumeImportTest {
    private final FileTextExtractor extractor = new FileTextExtractor();
    @Test void textKeepsParagraphsAndDoesNotSilentlyTruncate() {
        var text = new MockMultipartFile("file", "resume.txt", "text/plain", "Candidate\n\nProjects\nJava MySQL project".getBytes(StandardCharsets.UTF_8));
        String result = extractor.extract(text);
        assertTrue(result.contains("\n")); assertTrue(result.contains("Java MySQL"));
        var chinese = new MockMultipartFile("file", "中文简历.txt", "text/plain",
                "张三\n\n教育背景\n计算机科学本科\n\n项目经历\n使用 Java 和 MySQL 完成项目。".getBytes(StandardCharsets.UTF_8));
        assertTrue(extractor.extract(chinese).contains("教育背景"));
        var longText = new MockMultipartFile("file", "long.txt", "text/plain", "a".repeat(6001).getBytes(StandardCharsets.UTF_8));
        assertTrue(assertThrows(BusinessException.class, () -> extractor.extract(longText)).getMessage().contains("6000"));
    }
    @Test void docxAndPdfAreActuallyParsed() throws Exception {
        var archive = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(archive)) {
            entry(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            entry(zip, "word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Candidate Java MySQL project</w:t></w:r></w:p></w:body></w:document>");
        }
        assertTrue(extractor.extract(new MockMultipartFile("file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", archive.toByteArray())).contains("Java MySQL"));
        byte[] bytes;
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700); content.showText("Candidate Java MySQL project"); content.endText();
            }
            pdf.save(output); bytes=output.toByteArray();
        }
        assertTrue(extractor.extract(new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes)).contains("Java MySQL"));
    }
    @Test void emptyUnsupportedAndOversizeFilesFailWithoutCreatingResume() {
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]),
                new MockMultipartFile("file", "resume.exe", "application/octet-stream", new byte[]{1}),
                new MockMultipartFile("file", "huge.txt", "text/plain", new byte[5*1024*1024+1]),
                new MockMultipartFile("file", "blank.txt", "text/plain", "  \n ".getBytes(StandardCharsets.UTF_8))}) {
            assertThrows(BusinessException.class, () -> extractor.extract(file));
        }
    }
    private void entry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
