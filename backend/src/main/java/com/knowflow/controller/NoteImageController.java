package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.exception.BusinessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.util.*;
/** 图片嵌入笔记正文，访问权限随笔记继承；不产生公开静态附件URL。 */
@RestController @RequestMapping("/api/notes/images")
public class NoteImageController {
 @PostMapping public Result<String> upload(@RequestPart("file") MultipartFile file)throws java.io.IOException {
  SecurityUtils.getCurrentUserId();
  if(file.isEmpty()||file.getSize()>512*1024)throw new BusinessException(400,"图片须小于512KB");
  byte[] bytes=file.getBytes();
  try(var stream=ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new BusinessException(400,"仅支持PNG、JPEG和GIF图片");var reader=readers.next();
   try{reader.setInput(stream);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!Set.of("png","jpeg","jpg","gif").contains(format))throw new BusinessException(400,"不支持该图片格式");if((long)reader.getWidth(0)*reader.getHeight(0)>16000000)throw new BusinessException(400,"图片尺寸过大");return Result.success("data:image/"+(format.equals("jpg")?"jpeg":format)+";base64,"+Base64.getEncoder().encodeToString(bytes));}finally{reader.dispose();}
  }catch(javax.imageio.IIOException e){throw new BusinessException(400,"图片内容损坏，请重新选择");}
 }
}
