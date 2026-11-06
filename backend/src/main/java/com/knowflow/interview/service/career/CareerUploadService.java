package com.knowflow.interview.service.career;
import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.service.DocumentTextExtractor;
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

import com.knowflow.interview.career.CareerMatcher;
/** 内容探测与有界提取在事务外；成功的私人文件和版本原子保存。 */
@Service @RequiredArgsConstructor
public class CareerUploadService {
 public static final String NEEDS_TEXT_SOURCE = "[原文件未提取到可用正文，请手动粘贴简历文本后再确认]";
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
   String extension=detect(bytes);
   String filename=Objects.toString(input.getOriginalFilename(), "");
   int dot=filename.lastIndexOf('.');
   if(dot<0 || !extension.equals(filename.substring(dot+1).toLowerCase(Locale.ROOT)))throw new BusinessException(400,"文件扩展名与真实内容不符；只支持 PDF、DOC、DOCX、UTF-8 TXT");String type=switch(extension){case "pdf"->"application/pdf";case "doc"->"application/msword";case "docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";default->"text/plain";};
   var safe=new SafeFile(bytes,extension,type);
   String extracted=extractor.extractCareerText(safe);
   boolean unavailable=extracted==null||extracted.isBlank()||extracted.length()>50000;
   String warning=unavailable ? ("pdf".equals(extension)
     ? "PDF 未提取到可用文字，可能为扫描件、加密或损坏文件；暂不提供 OCR。原文件已私人保存，请手动粘贴正文。"
     : "未提取到可用正文或正文超过50000字；原文件已私人保存，请手动粘贴正文。")
     : (extracted.indexOf('\uFFFD')>=0 || extracted.chars().anyMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t')
       ? "正文包含异常字符，请对照原文件人工检查并修正后确认。" : "");
   String source=unavailable ? NEEDS_TEXT_SOURCE : extracted;
   var sections=unavailable ? Map.<String,List<CareerMatcher.Claim>>of() : CareerParsingService.previewSections(kind,source);
   String reviewWarning=sections.isEmpty()?warning:(warning+" 已按明确章节标题整理待审核条目；未识别内容保留在原文，请逐项校对后确认。").trim();
   return new TransactionTemplate(manager).execute(status->{
    var record=records.create(user,kind,new CareerRecordService.Edit(null,"RESUME".equals(kind)?"私人简历":"目标岗位",source,sections));
    jdbc.update("INSERT INTO interview_career_file(record_id,user_id,content_type,content_bytes) VALUES(?,?,?,?)",record.get("id"),user,type,bytes);
    var result=new LinkedHashMap<String,Object>(record);
    result.put("importStatus",unavailable ? "NEEDS_TEXT" : reviewWarning.isEmpty() ? "EXTRACTED" : "REVIEW_REQUIRED");
    result.put("importWarning",reviewWarning);
    return result;
   });
  }catch(BusinessException invalid){throw invalid;}
  catch(IOException invalid){throw new BusinessException(400,"文件内容无法读取");}
 }
 private String detect(byte[] bytes)throws IOException{
  if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))return "pdf";
  if(bytes.length>=8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1})){
   try(var fs=new org.apache.poi.poifs.filesystem.POIFSFileSystem(new ByteArrayInputStream(bytes))){
    if(fs.getRoot().hasEntry("WordDocument"))return "doc";
   }
   throw new BusinessException(400,"不是受支持的 DOC 文档");
  }
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
  catch(Exception invalid){throw new BusinessException(400,"只支持真实PDF、DOC、DOCX或UTF-8纯文本");}
 }
 public record Download(byte[] bytes,String contentType){}
 public Download download(Long user,long id){records.detail(user,id,null);var rows=jdbc.queryForList("SELECT content_bytes,content_type FROM interview_career_file WHERE record_id=? AND user_id=?",id,user);if(rows.isEmpty()||rows.get(0).get("content_bytes")==null)throw new BusinessException(404,"原始文件不存在");return new Download((byte[])rows.get(0).get("content_bytes"),String.valueOf(rows.get(0).get("content_type")));}
 /** 不把用户原始文件名交给解析器日志，也不允许解析器写出文件。 */
 private record SafeFile(byte[] bytes,String extension,String type) implements MultipartFile{
  public String getName(){return "file";}public String getOriginalFilename(){return "private-document."+extension;}public String getContentType(){return type;}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes.clone();}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File destination){throw new UnsupportedOperationException("私人上传不写出公共文件");}
 }
}
