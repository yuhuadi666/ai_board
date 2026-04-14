package org.example.aiboard.web;

import jakarta.validation.Valid;
import org.example.aiboard.dto.BoardRequest;
import org.example.aiboard.dto.BoardResponse;
import org.example.aiboard.service.MoodBoardService;
import org.example.aiboard.service.RemoveEntryResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/api/board", produces = MediaType.APPLICATION_JSON_VALUE)
public class BoardController {

    private final MoodBoardService moodBoardService;

    public BoardController(MoodBoardService moodBoardService) {
        this.moodBoardService = moodBoardService;
    }

    /**
     * 唯一接口：若 body 中带非空 {@code content} 则先写入心情并调用 LLM 生成暖心附言，再返回该访客全部留言；
     * 若仅传 {@code visitorId}（或 content 为空）则只返回历史列表。
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public BoardResponse board(@Valid @RequestBody BoardRequest request) {
        String content = request.content();
        if (content != null && !content.isBlank()) {
            moodBoardService.addEntry(request.visitorId(), content);
        }
        return BoardResponse.of(moodBoardService.listForVisitor(request.visitorId()));
    }

    @PatchMapping(path = "/entry/{entryId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public BoardResponse complete(
            @PathVariable("entryId") String entryId, @RequestParam("visitorId") String visitorId) {
        moodBoardService.completeEntry(visitorId, entryId);
        return BoardResponse.of(moodBoardService.listForVisitor(visitorId));
    }

    @DeleteMapping(path = "/entry/{entryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public BoardResponse delete(
            @PathVariable("entryId") String entryId, @RequestParam("visitorId") String visitorId) {
        return switch (moodBoardService.removeEntry(visitorId, entryId)) {
            case REMOVED -> BoardResponse.of(moodBoardService.listForVisitor(visitorId));
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "留言不存在");
            case COMPLETED_LOCKED ->
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "已完成留言不可删除");
        };
    }
}
