package com.knowflow.flashcards;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/my/flashcard-exchange")
public class FlashcardExchangeController {
 private final FlashcardExchangeService exchange;
 @PostMapping("/preview") public Result<FlashcardExchangeService.Preview> preview(@RequestBody FlashcardExchangeService.Request r){return Result.success(exchange.preview(SecurityUtils.getCurrentUserId(),r));}
 @PostMapping("/confirm") public Result<FlashcardExchangeService.Outcome> confirm(@RequestBody FlashcardExchangeService.Confirmation c){return Result.success(exchange.confirm(SecurityUtils.getCurrentUserId(),c));}
 @GetMapping("/export") public Result<Map<String,Object>> export(@RequestParam(defaultValue="csv") String format){return Result.success(exchange.export(SecurityUtils.getCurrentUserId(),format));}
}
