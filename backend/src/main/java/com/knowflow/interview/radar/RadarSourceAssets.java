package com.knowflow.interview.radar;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Private inline attachment documents; original chapter Markdown and its hash are never rewritten. */
@Component @RequiredArgsConstructor
public class RadarSourceAssets {
 private final JdbcTemplate jdbc;
 private final DocDocumentMapper documents;
 private final DocCategoryMapper categories;
 private final LearningChapterMapper chapters;
 private final DocumentAccessPolicy access;
 private static final Pattern DATA=Pattern.compile("^!\\[原始来源图]\\((data:image/(png|jpeg|gif);base64,([A-Za-z0-9+/=]+))\\)");
 private static final Pattern SOURCE=Pattern.compile("(?m)^## 来源：(.+)$");
 private record Asset(String path,String hash,String mime,String encoded){}
 private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 private List<Asset> validate(JsonNode input){
  var payload=input.path("imageAssets");if(payload.isMissingNode()||payload.isNull())return List.of();
  if(!payload.isArray()||payload.size()>100)throw new BusinessException(400,"图片清单无效或超过100项");
  Map<String,String> manifest=new HashMap<>();for(var file:input.path("files"))manifest.put(file.path("sourceFile").asText(),file.path("sourceHash").asText());
  List<Asset> result=new ArrayList<>();Set<String> paths=new HashSet<>();long total=0;
  for(var value:payload){
   String path=value.path("sourceFile").asText(),pin=value.path("sourceHash").asText(),mime=value.path("mimeType").asText(),encoded=value.path("base64").asText();
   if(path.isBlank()||path.startsWith("/")||path.contains("\\")||path.contains("\n")||path.contains("\r")||Arrays.asList(path.split("/")).contains("..")||!path.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|gif)")||!paths.add(path)||encoded.length()>710000)throw new BusinessException(400,"图片路径、大小或唯一身份无效");
   byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new BusinessException(400,"图片Base64无效");}
   total+=bytes.length;if(bytes.length==0||bytes.length>512*1024||total>8*1024*1024||!hash(bytes).equals(pin)||!pin.equals(manifest.get(path)))throw new BusinessException(400,"图片大小或来源指纹不匹配");
   try(var stream=ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new BusinessException(400,"图片内容不能识别");var reader=readers.next();
    try{reader.setInput(stream);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(format.equals("jpg"))format="jpeg";if(!Set.of("png","jpeg","gif").contains(format)||!mime.equals("image/"+format)||(long)reader.getWidth(0)*reader.getHeight(0)>16_000_000)throw new BusinessException(400,"图片格式或像素尺寸不支持");}
    finally{reader.dispose();}
   }catch(java.io.IOException e){throw new BusinessException(400,"图片内容损坏");}
   result.add(new Asset(path,pin,mime,Base64.getEncoder().encodeToString(bytes)));
  }
  return result;
 }
 public int preview(JsonNode input){return validate(input).size();}
 private String body(Asset asset){return "![原始来源图](data:"+asset.mime()+";base64,"+asset.encoded()+")\n\n来源："+asset.path()+"\nSHA-256："+asset.hash();}
 public int store(Long actor,Long category,JsonNode input){
  var cat=categories.selectById(category);if(cat==null||!actor.equals(cat.getOwnerId()))throw new BusinessException(403,"图片只能导入本人私有来源库");
  int created=0;for(var asset:validate(input)){
   String key="asset/"+hash(asset.path().getBytes(StandardCharsets.UTF_8));
   var existing=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,InterviewRadarImportService.KEY+actor,key);
   if(existing.isEmpty()){
    var doc=new DocDocument();String name=Path.of(asset.path()).getFileName().toString();doc.setTitle(("原始附件 · "+name).substring(0,Math.min(190,name.length()+7)));doc.setContent(body(asset));doc.setContentHash(asset.hash());doc.setSourcePath(asset.path());doc.setCategoryId(category);doc.setCategoryPath(category.toString());doc.setStatus(0);doc.setFileName(name);doc.setTags("interview-radar-private,source-image");doc.setWordCount(body(asset).length());documents.insert(doc);
    jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",InterviewRadarImportService.KEY+actor,key,"DOC",doc.getId());created++;
   }else{
    var doc=documents.selectById(existing.get(0));if(doc==null||!Objects.equals(doc.getCategoryId(),category)||!asset.path().equals(doc.getSourcePath()))throw new BusinessException(409,"附件已删除或归属冲突，不自动恢复");
    var image=DATA.matcher(Objects.toString(doc.getContent(),""));if(!image.find())throw new BusinessException(409,"附件正文已手工修改，未覆盖");
    byte[] previous;try{previous=Base64.getDecoder().decode(image.group(3));}catch(IllegalArgumentException e){throw new BusinessException(409,"附件数据已改变");}
    var expected=new Asset(asset.path(),doc.getContentHash(),"image/"+image.group(2),image.group(3));
    if(!hash(previous).equals(doc.getContentHash())||!body(expected).equals(doc.getContent()))throw new BusinessException(409,"附件正文或指纹已手工修改，未覆盖");
    if(!asset.hash().equals(doc.getContentHash())){doc.setContent(body(asset));doc.setContentHash(asset.hash());doc.setWordCount(body(asset).length());documents.updateById(doc);}
   }
  }return created;
 }
 public Map<String,String> forChapter(Long actor,Long chapterId){
  if(actor==null||chapterId==null)return Map.of();String pack=InterviewRadarImportService.KEY+actor;
  if(jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_type='CHAPTER' AND resource_id=?",Long.class,pack,chapterId).isEmpty())return Map.of();
  var chapter=chapters.selectById(chapterId);if(chapter==null)return Map.of();String raw=Objects.toString(chapter.getContent(),"");List<String> sources=new ArrayList<>();var source=SOURCE.matcher(raw);while(source.find())sources.add(source.group(1));
  Map<String,String> result=new LinkedHashMap<>();
  for(Long id:jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_type='DOC' AND resource_key LIKE 'asset/%'",Long.class,pack)){
   var doc=documents.selectById(id);if(doc==null||!access.canRead(doc,actor)||doc.getSourcePath()==null)continue;
   var image=DATA.matcher(Objects.toString(doc.getContent(),""));if(!image.find())continue;
   for(String file:sources){
    Path parent=Path.of(file).getParent();String relative=(parent==null?Path.of(doc.getSourcePath()):parent.relativize(Path.of(doc.getSourcePath()))).toString().replace('\\','/');
    String encoded=Arrays.stream(relative.split("/",-1)).map(s->URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20").replace("%7E","~")).collect(java.util.stream.Collectors.joining("/"));
    for(String alias:List.of(relative,encoded))if(raw.contains(alias)){
     String previous=result.putIfAbsent(alias,image.group(1));if(previous!=null&&!previous.equals(image.group(1)))throw new BusinessException(409,"相对图片别名冲突，需核定来源");
    }
   }
  }return Collections.unmodifiableMap(result);
 }
}
