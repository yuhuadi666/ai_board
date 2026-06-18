package org.example.aiboard.web;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.example.aiboard.dto.ExportResumeRequest;
import org.example.aiboard.dto.InitialRewriteResponse;
import org.example.aiboard.dto.ReviewRewriteRequest;
import org.example.aiboard.dto.ReviewRewriteResponse;
import org.example.aiboard.service.ResumeAgentService;
import org.example.aiboard.service.ResumeDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/api/resume/jd")
public class ResumeAgentController {

    private final ResumeDocumentService documentService;
    private final ResumeAgentService agentService;

    public ResumeAgentController(ResumeDocumentService documentService, ResumeAgentService agentService) {
        this.documentService = documentService;
        this.agentService = agentService;
    }

    @PostMapping(
            path = "/initial",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InitialRewriteResponse initial(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jd") String jd) {
        if (resume == null || resume.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传简历文件");
        }
        if (jd == null || jd.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写岗位 JD");
        }
        String resumeText = documentService.extractText(resume);
        if (resumeText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未能从简历文件中读取到文本内容");
        }
        return agentService.initialRewrite(resumeText, jd);
    }

    @PostMapping(
            path = "/review",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ReviewRewriteResponse review(@Valid @RequestBody ReviewRewriteRequest request) {
        return agentService.reviewAndRewrite(
                request.currentResume(),
                request.jd(),
                request.confirmedChanges());
    }

    @PostMapping(
            path = "/export",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportResumeRequest request) {
        String finalResume = agentService.applyChanges(request.resumeText(), request.acceptedChanges());
        byte[] content = documentService.buildDocx(finalResume);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("resume-agent-output.docx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(content);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
