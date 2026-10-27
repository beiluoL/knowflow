package com.knowflow.security;

import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.DocDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import java.util.regex.Pattern;

/** Durable first-source attachment ownership. Text references cannot transfer ownership. */
@Component @RequiredArgsConstructor
public class DocumentAssetBindings {
    private final JdbcTemplate sql;
    private final DocDocumentMapper docs;
    private static final Pattern URL=Pattern.compile("/uploads/[^\\s\\\"'<>())]+");
    private String key(String url){return cn.hutool.crypto.digest.DigestUtil.sha256Hex(url);}
    public Long owner(String url){var rows=sql.queryForList("select doc_id from doc_asset_binding where asset_key=?",Long.class,key(url));return rows.isEmpty()?null:rows.get(0);}
    private void bind(String url,Long docId){if(url==null||!url.startsWith("/uploads/"))return;try{sql.update("insert into doc_asset_binding(asset_key,asset_url,doc_id) values(?,?,?)",key(url),url,docId);}catch(DuplicateKeyException concurrentFirstBinding){/* Never overwrite another source. */}}
    public Long captureLegacy(String url){Long id=owner(url);if(id!=null)return id;String pattern="%"+url.replace("!","!!").replace("%","!%").replace("_","!_")+"%";Long first=docs.findAssetReferences(url,pattern).stream().min(Comparator.comparing(DocDocument::getId)).map(DocDocument::getId).orElse(0L);bind(url,first);return owner(url);}
    public Set<String> urls(DocDocument doc){Set<String> urls=new HashSet<>();for(String value:Arrays.asList(doc.getContent(),doc.getCover(),doc.getFileUrl())){if(value==null)continue;var matcher=URL.matcher(value);while(matcher.find())urls.add(matcher.group());}return urls;}
    public void retain(DocDocument existing){for(String url:urls(existing))captureLegacy(url);}
    /** Call before accepting arbitrary client references: unknown legacy assets remain public. */
    public void clientReferences(DocDocument input){for(String url:urls(input))captureLegacy(url);}
    public void trustedAsset(String url,Long docId){bind(url,docId);}
}
