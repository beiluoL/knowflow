package com.knowflow.interview.speech;
import com.knowflow.common.Result;
import com.knowflow.exception.BusinessException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
/** 语音接口明确返回真实 HTTP 状态，避免旧全局处理器把 503 包成 200。 */
@Order(0) @RestControllerAdvice(assignableTypes=InterviewSpeechController.class)
public class InterviewSpeechExceptionHandler {
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<Void>> business(BusinessException e){int code=e.getCode();return ResponseEntity.status(code>=400&&code<=599?code:500).body(Result.error(code,e.getMessage()));}
}
