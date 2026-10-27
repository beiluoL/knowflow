package com.knowflow.flashcards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Content exchange only. All instances and review events remain in the existing learning tables. */
@Service
@RequiredArgsConstructor
public class FlashcardExchangeService {
    private final JdbcTemplate jdbc;
    private final FlashcardNoteService notes;
    private final ObjectMapper json;
    private final byte[] signingKey = new SecureRandom().generateSeed(32);
    private static final List<String> FIELDS = List.of("type","front","back","language","category","tags","deck","source","externalId","textEncoding");
    @Data public static class Request {
        private String format = "csv", content, source = "personal-import";
        // Semantic field -> zero-based source column; an absent field auto-matches a canonical header.
        private Map<String,Integer> mapping = new TreeMap<>();
    }
    @Data public static class Confirmation {
        private Request request; private String token; private boolean skipProblems;
    }
    public record Item(int line, String status, String message, String identity, String fingerprint,
                       String source, String externalId, String deck, FlashcardNoteService.Input note, int cards) {}
    public record Preview(List<String> headers, List<String> warnings, List<Item> rows, Map<String,Integer> counts,
                          int cardCount, String token) {}
    public record Outcome(int insertedNotes, int insertedCards, int skipped, List<Long> noteIds) {}
    @PostConstruct void initialize() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS learning_flashcard_import_owner (user_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS learning_flashcard_import_source (user_id BIGINT NOT NULL,identity_key VARCHAR(64) NOT NULL,source_name VARCHAR(200) NOT NULL,external_id VARCHAR(200) NOT NULL,deck_name VARCHAR(100) NOT NULL,fingerprint VARCHAR(64) NOT NULL,note_id BIGINT NOT NULL,PRIMARY KEY(user_id,identity_key),UNIQUE(note_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS learning_flashcard_import_receipt (user_id BIGINT NOT NULL,token_hash VARCHAR(64) NOT NULL,result_json TEXT NOT NULL,PRIMARY KEY(user_id,token_hash))");
    }
    public Preview preview(long uid, Request request) {
        var p = analyze(uid, request);
        long issued = Instant.now().getEpochSecond();
        String payload = uid + ":" + issued + ":" + requestHash(request) + ":" + snapshot(p);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
        return new Preview(p.headers(), p.warnings(), p.rows(), p.counts(), p.cardCount(), token);
    }
    private Preview analyze(long uid, Request r) {
        if (r == null || !("csv".equals(r.getFormat()) || "tsv".equals(r.getFormat()))) throw bad("仅支持UTF-8 CSV / TSV；apkg尚未开放");
        if (r.getSource() == null || r.getSource().isBlank() || r.getSource().length() > 200) throw bad("来源标识必填且最多200字符");
        if (r.getMapping() == null || r.getMapping().size() > FIELDS.size()) throw bad("字段映射无效");
        List<FlashcardDelimitedText.Row> parsed;
        try { parsed = FlashcardDelimitedText.parse(r.getContent(), "csv".equals(r.getFormat()) ? ',' : '\t'); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
        var headers = parsed.get(0).cells();
        if (headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size() != headers.size()) throw bad("表头不能为空或重复");
        Map<String,Integer> mapping = new TreeMap<>();
        for (String f : FIELDS) { int i = headers.indexOf(f); if (i >= 0) mapping.put(f,i); }
        for (var entry : r.getMapping().entrySet()) {
            if (!FIELDS.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < -1 || entry.getValue() >= headers.size()) throw bad("字段映射超出表头范围");
            if (entry.getValue() == -1) mapping.remove(entry.getKey()); else mapping.put(entry.getKey(), entry.getValue());
        }
        if (!mapping.containsKey("front")) throw bad("请映射正面 / Cloze原文列");
        if (new HashSet<>(mapping.values()).size() != mapping.size()) throw bad("不同字段不能重复使用同一列");
        List<String> warnings = new ArrayList<>();
        for (int i=0;i<headers.size();i++) if (!mapping.containsValue(i)) warnings.add("忽略未映射列：" + headers.get(i));
        warnings.add("仅导入内容；外部复习历史不转换为FSRS。卡组列暂为来源元数据，不是独立卡组管理。");
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (String s:List.of("NEW","DUPLICATE","CONFLICT","INVALID")) counts.put(s,0);
        List<Item> items = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        int cardCount=0;
        for (int n=1;n<parsed.size();n++) {
            var row=parsed.get(n);
            String identity="", fingerprint="", source="", external="", deck="";
            FlashcardNoteService.Input note = new FlashcardNoteService.Input(); int cards=0;
            String state="NEW", message="新增";
            try {
                if (row.cells().size()!=headers.size()) throw bad("列数与表头不一致");
                Map<String,String> values = new HashMap<>();
                for (var e:mapping.entrySet()) values.put(e.getKey(),row.cells().get(e.getValue()));
                String encoding=values.getOrDefault("textEncoding","");
                if (!encoding.isEmpty() && !encoding.equals("apostrophe-v1")) throw bad("不支持的内容编码");
                if (encoding.equals("apostrophe-v1")) values.replaceAll((k,v)->!k.equals("textEncoding") && v.startsWith("'") ? v.substring(1) : v);
                note.setType(value(values,"type","BASIC").toUpperCase(Locale.ROOT));
                note.setFront(values.getOrDefault("front","")); note.setBack(values.getOrDefault("back",""));
                note.setLanguage(value(values,"language","java")); note.setCategory(values.getOrDefault("category","")); note.setTags(values.getOrDefault("tags",""));
                source=value(values,"source",r.getSource()); external=values.getOrDefault("externalId",""); deck=values.getOrDefault("deck","");
                if (source.length()>200 || external.length()>200 || deck.length()>100) throw bad("来源/外部编号最多200字符，卡组最多100字符");
                // Markdown is allowed. Raw HTML is rejected instead of trusting external templates or SVG.
                if ((!note.getType().equals("CODE_CLOZE") && hasHtml(note.getFront())) || hasHtml(note.getBack())) throw bad("不接受外部HTML模板，请转换为Markdown或纯文本");
                if (unsafeResource(note.getFront()) || unsafeResource(note.getBack())) throw bad("不接受外部图片/媒体或脚本链接，请导入后通过授权附件添加");
                var expanded=notes.preview(note);
                var projected=(List<?>) expanded.get("cards"); cards=projected.size();
                for(Object projection:projected){var fields=(Map<?,?>)projection;for(String key:List.of("front","back"))if(fields.get(key).toString().getBytes(StandardCharsets.UTF_8).length>60000)throw bad("生成卡片内容超过存储上限，请拆分Note");}
                if (cards>100) throw bad("单Note最多100张卡");
                fingerprint=fingerprint(note,deck);
                identity=hash(jsonText(List.of(source, external.isEmpty()?"fingerprint:"+fingerprint:"external:"+external)));
                // Our own content exports carry stable original IDs; verify ownership AND full content.
                if (source.equals("knowflow:"+uid) && external.matches("(note|card):[0-9]+")) {
                    boolean ownNote=external.startsWith("note:"); long localId;
                    try {localId=Long.parseLong(external.substring(external.indexOf(':')+1));}catch(NumberFormatException e){throw bad("外部编号超出范围");}
                    var locals=jdbc.queryForList(ownNote?"SELECT * FROM learning_flashcard_note WHERE id=? AND user_id=?":"SELECT * FROM learning_flashcard WHERE id=? AND user_id=? AND deleted=0",localId,uid);
                    if(!locals.isEmpty()) {
                        var local=locals.get(0); var localNote=new FlashcardNoteService.Input();
                        localNote.setType(ownNote?text(local,"note_type"):"BASIC");localNote.setFront(text(local,"front"));localNote.setBack(text(local,"back"));
                        localNote.setLanguage(ownNote?text(local,"language"):"java");localNote.setCategory(text(local,"category"));localNote.setTags(text(local,"tags"));
                        boolean active=true;
                        if(ownNote){int count=jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_note_card m JOIN learning_flashcard c ON c.id=m.card_id WHERE m.note_id=? AND c.user_id=? AND c.deleted=0",Integer.class,localId,uid);active=count==cards;}
                        boolean matches=active && fingerprint.equals(fingerprint(localNote,""));
                        state=matches?"DUPLICATE":"CONFLICT";message=matches?"本人原卡的相同内容导出":"本人原卡已变化，保留现有内容";
                    }
                }
                String batch=seen.putIfAbsent(identity,fingerprint);
                if (batch!=null) { state=batch.equals(fingerprint)?"DUPLICATE":"CONFLICT"; message=batch.equals(fingerprint)?"文件内重复":"文件内同来源编号内容冲突"; }
                else if(state.equals("NEW")) {
                    var links=jdbc.queryForList("SELECT * FROM learning_flashcard_import_source WHERE user_id=? AND identity_key=?",uid,identity);
                    if (!links.isEmpty()) {
                        var link=links.get(0); long noteId=((Number)link.get("note_id")).longValue();
                        var current=jdbc.queryForList("SELECT * FROM learning_flashcard_note WHERE id=? AND user_id=?",noteId,uid);
                        boolean intact=false;
                        if (!current.isEmpty()) {
                            var c=current.get(0); var input=new FlashcardNoteService.Input();
                            input.setType((String)c.get("note_type")); input.setFront((String)c.get("front")); input.setBack((String)c.get("back")); input.setLanguage((String)c.get("language")); input.setCategory((String)c.get("category")); input.setTags((String)c.get("tags"));
                            int active=jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_note_card m JOIN learning_flashcard c ON c.id=m.card_id WHERE m.note_id=? AND c.user_id=? AND c.deleted=0",Integer.class,noteId,uid);
                            intact=fingerprint.equals(fingerprint(input,Objects.toString(link.get("deck_name"),""))) && active==cards;
                        }
                        state=intact?"DUPLICATE":"CONFLICT"; message=intact?"本人已导入相同来源与内容":"来源已存在但内容或卡片已变化；保留原卡，可跳过或取消";
                    }
                }
            } catch (BusinessException | IllegalArgumentException e) { state="INVALID"; message=e.getMessage(); }
            counts.merge(state,1,Integer::sum);
            if(state.equals("NEW")) cardCount+=cards;
            items.add(new Item(row.line(),state,message,identity,fingerprint,source,external,deck,note,cards));
        }
        if(cardCount>2000) throw bad("一次最多生成2000张卡，请拆分文件");
        return new Preview(headers,warnings,items,counts,cardCount,null);
    }
    @Transactional
    public Outcome confirm(long uid, Confirmation c) {
        if(c==null || c.getToken()==null || c.getToken().length()>1000 || c.getRequest()==null) throw conflict("请先预览");
        if(c.getRequest().getContent()==null || c.getRequest().getContent().length()>FlashcardDelimitedText.MAX_BYTES)throw bad("文件为空或超过1MiB");
        String payload=verifyToken(uid,c.getRequest(),c.getToken());
        jdbc.update("INSERT INTO learning_flashcard_import_owner(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id",uid);
        jdbc.queryForList("SELECT user_id FROM learning_flashcard_import_owner WHERE user_id=? FOR UPDATE",uid);
        String tokenHash=hash(c.getToken());
        var receipt=jdbc.queryForList("SELECT result_json FROM learning_flashcard_import_receipt WHERE user_id=? AND token_hash=?",uid,tokenHash);
        if(!receipt.isEmpty()) { try{return json.readValue((String)receipt.get(0).get("result_json"),Outcome.class);}catch(Exception e){throw new IllegalStateException("Invalid import receipt",e);} }
        var p=analyze(uid,c.getRequest());
        if(!payload.endsWith(":"+snapshot(p))) throw conflict("导入来源已变化，请重新预览");
        if(!c.isSkipProblems() && p.counts().get("INVALID")+p.counts().get("CONFLICT")>0) throw conflict("存在无效或冲突行，请修正或明确选择跳过");
        List<Long> noteIds=new ArrayList<>(); int cards=0;
        for(var row:p.rows()) if(row.status().equals("NEW")) {
            var created=notes.create(uid,row.note()); long id=((Number)created.get("id")).longValue();
            jdbc.update("INSERT INTO learning_flashcard_import_source(user_id,identity_key,source_name,external_id,deck_name,fingerprint,note_id) VALUES(?,?,?,?,?,?,?)",uid,row.identity(),row.source(),row.externalId(),row.deck(),row.fingerprint(),id);
            noteIds.add(id); cards+=row.cards();
            jdbc.update("UPDATE learning_flashcard SET source_type='IMPORT' WHERE user_id=? AND id IN (SELECT card_id FROM learning_flashcard_note_card WHERE note_id=?)",uid,id);
        }
        var result=new Outcome(noteIds.size(),cards,p.rows().size()-noteIds.size(),noteIds);
        jdbc.update("INSERT INTO learning_flashcard_import_receipt(user_id,token_hash,result_json) VALUES(?,?,?)",uid,tokenHash,jsonText(result));
        return result;
    }
    public Map<String,Object> export(long uid,String format) {
        if(!Set.of("csv","tsv").contains(format)) throw bad("仅支持CSV / TSV内容导出");
        var rows=jdbc.queryForList("SELECT n.*,s.source_name,s.external_id,s.deck_name FROM learning_flashcard_note n LEFT JOIN learning_flashcard_import_source s ON s.note_id=n.id AND s.user_id=n.user_id WHERE n.user_id=? AND EXISTS(SELECT 1 FROM learning_flashcard_note_card m JOIN learning_flashcard c ON c.id=m.card_id WHERE m.note_id=n.id AND c.user_id=n.user_id AND c.deleted=0) ORDER BY n.id LIMIT 1001",uid);
        var legacy=jdbc.queryForList("SELECT c.* FROM learning_flashcard c WHERE c.user_id=? AND c.deleted=0 AND NOT EXISTS(SELECT 1 FROM learning_flashcard_note_card m WHERE m.card_id=c.id) ORDER BY c.id LIMIT 1001",uid);
        if(rows.size()+legacy.size()>1000) throw bad("内容超过1000行，请先分批整理；未生成截断导出");
        int expandedCount=legacy.size();
        for(var note:rows){
            if(text(note,"note_type").endsWith("CLOZE")) {
                try{expandedCount+=ClozeParser.cards(text(note,"front"),text(note,"back")).size();}
                catch(IllegalArgumentException e){throw bad("存在无效Cloze笔记，请修正后导出");}
            } else expandedCount++;
            if(expandedCount>2000)throw bad("内容超过2000张独立卡片，请分批整理；未生成不可重导文件");
        }
        List<List<String>> output=new ArrayList<>(); output.add(FIELDS);
        for(var n:rows) output.add(safeRow(List.of(text(n,"note_type"),text(n,"front"),text(n,"back"),text(n,"language"),text(n,"category"),text(n,"tags"),text(n,"deck_name"),n.get("source_name")==null?"knowflow:"+uid:text(n,"source_name"),n.get("source_name")==null?"note:"+n.get("id"):text(n,"external_id"),"apostrophe-v1")));
        for(var c:legacy) output.add(safeRow(List.of("BASIC",text(c,"front"),text(c,"back"),"java",text(c,"category"),text(c,"tags"),"","knowflow:"+uid,"card:"+c.get("id"),"apostrophe-v1")));
        String content=FlashcardDelimitedText.encode(output,format.equals("csv")?',':'\t');
        if(content.getBytes(StandardCharsets.UTF_8).length>FlashcardDelimitedText.MAX_BYTES) throw bad("内容超过1MiB，未生成不可重导的超限文件");
        return Map.of("format",format,"content",content,"notes",output.size()-1,"warning","仅内容导出，不含FSRS状态、DueAt或历史；外部媒体需另行授权。公式字符已做可逆文本转义。");
    }
    private static List<String> safeRow(List<String> row){return row.stream().map(v->v.startsWith("'") || v.stripLeading().matches("(?s)^[=+@\\-].*") || v.startsWith("\t") ? "'"+v:v).toList();}
    private boolean hasHtml(String s){return s.matches("(?s).*<\\s*[/!?a-zA-Z][^>]*>.*");}
    private boolean unsafeResource(String s){String lower=s.toLowerCase(Locale.ROOT);return lower.contains("![") || lower.contains("javascript:") || lower.contains("vbscript:") || lower.contains("data:") || lower.contains("[sound:");}
    private String value(Map<String,String> values,String key,String fallback){String v=values.get(key);return v==null||v.isEmpty()?fallback:v;}
    private String fingerprint(FlashcardNoteService.Input n,String deck){return hash(jsonText(Arrays.asList(n.getType(),n.getFront(),n.getBack(),n.getLanguage(),Objects.toString(n.getCategory(),""),Objects.toString(n.getTags(),""),deck)));}
    private String snapshot(Preview p){return hash(jsonText(p.rows().stream().map(r->List.of(r.line(),r.status(),r.identity(),r.fingerprint())).toList()));}
    private String requestHash(Request r){return hash(jsonText(Arrays.asList(r.getFormat(),r.getContent(),r.getSource(),r.getMapping()==null?null:new TreeMap<>(r.getMapping()))));}
    private String verifyToken(long uid,Request request,String token){
        try {
            String[] parts=token.split("\\."); if(parts.length!=2)throw new IllegalArgumentException();
            String payload=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);
            if(!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
            String[] values=payload.split(":");long issued=Long.parseLong(values[1]),now=Instant.now().getEpochSecond();
            if(values.length!=4 || Long.parseLong(values[0])!=uid || issued>now || now-issued>900 || !values[2].equals(requestHash(request)))throw new IllegalArgumentException();
            return payload;
        }catch(Exception e){throw conflict("预览已失效或内容已变化，请重新预览（有效期15分钟，服务重启后需重试）");}
    }
    private String sign(String value){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(signingKey,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    private String jsonText(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static BusinessException bad(String s){return new BusinessException(400,s);}
    private static BusinessException conflict(String s){return new BusinessException(409,s);}
}
