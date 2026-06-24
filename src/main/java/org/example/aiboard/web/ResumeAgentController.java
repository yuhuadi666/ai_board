package org.example.aiboard.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping(value = "/api/resume/jd")
public class ResumeAgentController {

    private static final Logger log = LoggerFactory.getLogger(ResumeAgentController.class);

    private final ResumeDocumentService documentService;
    private final ResumeAgentService agentService;
    private final ObjectMapper objectMapper;

    public ResumeAgentController(
            ResumeDocumentService documentService,
            ResumeAgentService agentService,
            ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = "/initial",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InitialRewriteResponse initial(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jd") String jd) {
        return agentService.initialRewrite(validateInitialInput(resume, jd), jd);
    }

    @PostMapping(
            path = "/initial/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter initialStream(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jd") String jd) {
        String resumeText = validateInitialInput(resume, jd);
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        CompletableFuture.runAsync(() -> {
            try {
                agentService.initialRewriteStream(
                        resumeText,
                        jd,
                        chunk -> sendEvent(emitter, closed, "thinking", chunk),
                        result -> {
                            try {
                                sendEvent(emitter, closed, "done", objectMapper.writeValueAsString(result));
                            } catch (IOException e) {
                                log.warn("failed to serialize stream result: {}", e.getMessage());
                            } finally {
                                finish(emitter, closed);
                            }
                        },
                        error -> {
                            sendEvent(emitter, closed, "error", error.getMessage() == null ? "生成失败" : error.getMessage());
                            finish(emitter, closed);
                        });
            } catch (Exception e) {
                log.warn("initial stream failed: {}", e.getMessage());
                sendEvent(emitter, closed, "error", e.getMessage() == null ? "生成失败" : e.getMessage());
                finish(emitter, closed);
            }
        });
        emitter.onTimeout(() -> finish(emitter, closed));
        emitter.onError(error -> finish(emitter, closed));
        return emitter;
    }

    private String validateInitialInput(MultipartFile resume, String jd) {
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
        return resumeText;
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String name, String data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.debug("sse send failed (event={}): {}", name, e.getMessage());
            finish(emitter, closed);
        }
    }

    private void finish(SseEmitter emitter, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("sse complete skipped: {}", e.getMessage());
        }
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
