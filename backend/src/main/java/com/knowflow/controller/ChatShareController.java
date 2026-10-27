package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.ChatShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequiredArgsConstructor
public class ChatShareController {
 private final ChatShareService shares;
 public record CreateShare(Integer expiresInHours,Boolean confirmed){}
 private long user(){Long id=SecurityUtils.getCurrentUserIdNullable();if(id==null)throw new BusinessException(401,"未登录或登录已失效");return id;}
 @PostMapping("/api/chat/conversations/{id}/shares")
 public Result<Map<String,Object>> create(@PathVariable long id,@RequestBody CreateShare body){return Result.success(shares.create(id,user(),body.expiresInHours(),body.confirmed()));}
 @GetMapping("/api/chat/conversations/{id}/shares")
 public Result<List<Map<String,Object>>> list(@PathVariable long id){return Result.success(shares.list(id,user()));}
 @DeleteMapping("/api/chat/conversations/{id}/shares/{shareId}")
 public Result<Void> revoke(@PathVariable long id,@PathVariable long shareId){shares.revoke(id,user(),shareId);return Result.success();}
 @GetMapping("/api/public/chat-shares/{token}")
 public Result<Map<String,Object>> read(@PathVariable String token){return Result.success(shares.read(token));}
}
