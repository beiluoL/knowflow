package com.knowflow.interview.bridge;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interview/v2/sessions/{id}/remediation")
public class InterviewLearningBridgeController {
    private final InterviewLearningBridge bridge;
    @ExceptionHandler(com.knowflow.exception.BusinessException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> business(com.knowflow.exception.BusinessException ex) {
        int code = ex.getCode() == null ? 500 : ex.getCode();
        int status = code >= 400 && code <= 599 ? code : 500;
        return org.springframework.http.ResponseEntity.status(status).body(Result.error(status, ex.getMessage()));
    }
    @GetMapping
    public Result<InterviewLearningBridge.Preview> preview(@PathVariable Long id) {
        return Result.success(bridge.preview(SecurityUtils.getCurrentUserId(),id));
    }
    @PostMapping("/confirm")
    public Result<List<InterviewLearningBridge.Confirmed>> confirm(@PathVariable Long id, @RequestBody InterviewLearningBridge.ConfirmRequest request) {
        return Result.success(bridge.confirm(SecurityUtils.getCurrentUserId(),id,request));
    }
}
