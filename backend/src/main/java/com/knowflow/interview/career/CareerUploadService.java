package com.knowflow.interview.career;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.DocumentTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

/** 内容探测与有界提取在事务外；成功的私人文件和版本原子保存。 */
@Service @RequiredArgsConstructor
public class CareerUploadService {
 private static final long MAX_BYTES=5L*1024*1024;
 private final DocumentTextExtractor extractor;
 private final CareerRecordService records;
 private final JdbcTemplate jdbc;
 private final PlatformTransactionManager manager;
 public Map<String,Object> upload(Long user,String kind,MultipartFile input){
  if(user==null)throw new BusinessException(401,"请先登录");
  if(input==null||input.isEmpty()||input.getSize()>MAX_BYTES)throw new BusinessException(400,"文件必须为1字节到5MB");
  try{
   byte[] bytes=input.getBytes();if(bytes.length>MAX_BYTES)throw new BusinessException(400,"文件超过5MB");
   String extension=detect(bytes);String type=switch(extension){case "pdf"->"application/pdf";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";default->"text/plain";};
   var safe=new SafeFile(bytes,extension,type);
   String source=extractor.extractText(safe);
   if(source==null||source.isBlank()||source.length()>50000)throw new BusinessException(422,"提取失败或超过50000字；请使用可复制文本的PDF、DOCX或TXT重新上传");
   return new TransactionTemplate(manager).execute(status->{
    var record=records.create(user,kind,new CareerRecordService.Edit(null,"RESUME".equals(kind)?"私人简历":"目标岗位",source,Map.of()));
    jdbc.update("INSERT INTO interview_career_file(record_id,user_id,content_type,content_bytes) VALUES(?,?,?,?)",record.get("id"),user,type,bytes);
    return record;
   });
  }catch(BusinessException invalid){throw invalid;}
  catch(IOException invalid){throw new BusinessException(400,"文件内容无法读取");}
 }
 private String detect(byte[] bytes)throws IOException{
  if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))return "pdf";
  if(bytes.length>=4&&bytes[0]=='P'&&bytes[1]=='K'){
   boolean document=false,types=false;long total=0;int entries=0;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
   try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
    for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
     if(++entries>1000)throw new BusinessException(400,"压缩结构超出安全限制");
     document|="word/document.xml".equals(entry.getName());types|="[Content_Types].xml".equals(entry.getName());
     byte[] buffer=new byte[8192];int count;
     while((count=zip.read(buffer))!=-1){total+=count;if(total>10L*1024*1024||System.nanoTime()>deadline)throw new BusinessException(400,"文档展开超过安全限制");}
    }
   }
   if(document&&types)return "docx";throw new BusinessException(400,"不是受支持的DOCX文档");
  }
  try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();if(text.indexOf('\0')>=0)throw new IllegalArgumentException();return "txt";}
  catch(Exception invalid){throw new BusinessException(400,"只支持真实PDF、DOCX或UTF-8纯文本");}
 }
 public record Download(byte[] bytes,String contentType){}
 public Download download(Long user,long id){records.detail(user,id,null);var rows=jdbc.queryForList("SELECT content_bytes,content_type FROM interview_career_file WHERE record_id=? AND user_id=?",id,user);if(rows.isEmpty()||rows.get(0).get("content_bytes")==null)throw new BusinessException(404,"原始文件不存在");return new Download((byte[])rows.get(0).get("content_bytes"),String.valueOf(rows.get(0).get("content_type")));}
 /** 不把用户原始文件名交给解析器日志，也不允许解析器写出文件。 */
 private record SafeFile(byte[] bytes,String extension,String type) implements MultipartFile{
  public String getName(){return "file";}public String getOriginalFilename(){return "private-document."+extension;}public String getContentType(){return type;}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes.clone();}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File destination){throw new UnsupportedOperationException("私人上传不写出公共文件");}
 }
}
