package com.wanbinyu.resumefit.resume;

import com.wanbinyu.resumefit.common.CurrentUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/resumes/{resumeId}/versions/{versionId}/document")
public class ResumeDocumentController {
    private final ResumeDocumentService documents;
    public ResumeDocumentController(ResumeDocumentService documents){this.documents=documents;}
    @GetMapping public ResponseEntity<byte[]> pdf(Authentication auth,@PathVariable Long resumeId,@PathVariable Long versionId,
            @RequestParam(defaultValue="") String targetRole){
        byte[] result=documents.generate(CurrentUser.id(auth),resumeId,versionId,targetRole);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header("Content-Disposition","inline; filename=resume-v"+versionId+".pdf").header("X-Content-Type-Options","nosniff").body(result);
    }
}
