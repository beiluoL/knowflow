package com.knowflow.interview.voice;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 票据仅通过已鉴权HTTP发放；JWT从不进入语音WebSocket URL。 */
@RestController
@RequestMapping("/api/interview/v2/sessions/{id}/voice")
@RequiredArgsConstructor
public class VoiceController {

  private final VoiceAccess access;

  @GetMapping("/capabilities")
  public Result<?> capabilities(@PathVariable long id) {
    return Result.success(
      access.capability(SecurityUtils.getCurrentUserId(), id)
    );
  }

  @PostMapping("/ticket")
  public Result<?> ticket(
    @PathVariable long id,
    @RequestHeader(value = "Origin", required = false) String origin
  ) {
    return Result.success(
      access.ticket(SecurityUtils.getCurrentUserId(), id, origin)
    );
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Result<?>> error(BusinessException failure) {
    int code = failure.getCode();
    if (code < 400 || code > 599) code = 500;
    return ResponseEntity.status(code).body(
      Result.error(code, failure.getMessage())
    );
  }
}
