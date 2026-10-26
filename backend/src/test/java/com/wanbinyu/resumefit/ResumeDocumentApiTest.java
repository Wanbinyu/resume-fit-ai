package com.wanbinyu.resumefit;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.ai.demo-mode=true","spring.datasource.url=jdbc:h2:mem:resume_document;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class ResumeDocumentApiTest {
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.storage.upload-dir",()->uploads.toString());}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,String token,Object body)throws Exception{
        if(token!=null)req.header("Authorization","Bearer "+token);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response=mvc.perform(req).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());
        return json.readTree(response.getContentAsByteArray()).path("data");
    }
    String user()throws Exception{return request(post("/api/auth/register"),null,Map.of("username","document-"+UUID.randomUUID().toString().substring(0,12),"password","document-test-password-123","displayName","测试用户")).path("token").asText();}
    long resume(String token)throws Exception{return request(post("/api/resumes"),token,Map.of("title","测试简历","content","合成测试候选人\n\n项目经历\n原始课程项目：Java 与 MySQL。","sourceFileName","synthetic.txt")).path("id").asLong();}

    @Test void imageProcessingRoutesAndControlsAreRemoved()throws Exception{
        String token=user();long id=resume(token);
        assertEquals(400,mvc.perform(get("/api/resumes/photo-capabilities").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        for(String path:List.of("/api/resumes/"+id+"/photo","/api/resumes/"+id+"/photo/original","/api/resumes/"+id+"/photo/cutout")){
            assertEquals(404,mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus(),path);
        }
        assertEquals(404,mvc.perform(multipart("/api/resumes/"+id+"/photo").file(new MockMultipartFile("file","removed.png","image/png",new byte[]{1,2,3})).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        assertEquals(404,mvc.perform(delete("/api/resumes/"+id+"/photo").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        String html=mvc.perform(get("/workspace.html")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(html.contains("photoPanel"));assertFalse(html.contains("includePhoto"));assertFalse(html.contains("/photo"));
        assertTrue(html.contains("previewDocumentBtn"));
    }

    @Test void pdfPreservesSavedChineseContentAcrossPagesWithoutImages()throws Exception{
        Assumptions.assumeTrue(Files.isRegularFile(Path.of("C:/Windows/Fonts/simhei.ttf")),"Configure a Chinese TTF font on non-Windows hosts");
        String token=user(),other=user();long id=resume(token);
        String body="合成测试候选人\n\n教育背景\n计算机科学本科\n\n项目经历\n经核实的 Java 后端接口项目。\n"+"使用 MySQL 完成查询与数据校验，保留真实职责与交付结果。\n".repeat(90)+"\n专业技能\nJava MySQL Git Linux\n最后一行验证标记";
        long version=request(post("/api/resumes/"+id+"/versions"),token,Map.of("title","调整后的已保存版本","content",body,"source","MANUAL")).path("id").asLong();
        String path="/api/resumes/"+id+"/versions/"+version+"/document";
        // Old bookmarks with removed options must still produce a text-only document.
        var response=mvc.perform(get(path).param("photo","true").param("background","blue").param("targetRole","Java 后端开发工程师").header("Authorization","Bearer "+token)).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());assertEquals("application/pdf",response.getContentType());
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
        try(var pdf=Loader.loadPDF(response.getContentAsByteArray())){
            assertTrue(pdf.getNumberOfPages()>=3);String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("合成测试候选人"));assertTrue(text.contains("经核实的 Java"));assertTrue(text.contains("最后一行验证标记"));assertTrue(text.contains("Java 后端开发工程师"));assertFalse(text.contains("原始课程项目"));
            for(var page:pdf.getPages())assertFalse(page.getResources().getXObjectNames().iterator().hasNext());
        }
        assertEquals(2,request(get("/api/resumes/"+id+"/versions"),token,null).size());
        assertEquals(400,mvc.perform(get(path).header("Authorization","Bearer "+other)).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get(path).param("targetRole","长".repeat(121)).header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        assertNotEquals(200,mvc.perform(get(path)).andReturn().getResponse().getStatus());
    }
}
