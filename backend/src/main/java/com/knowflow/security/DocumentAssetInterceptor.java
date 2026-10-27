package com.knowflow.security;

import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.exception.BusinessException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

/** Authorization follows persistent source ownership, not freely editable text references. */
@Component @RequiredArgsConstructor
public class DocumentAssetInterceptor implements HandlerInterceptor {
    private final DocDocumentMapper docs;
    private final DocumentAccessPolicy access;
    private final DocumentAssetBindings bindings;
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        String url=UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length()),StandardCharsets.UTF_8);
        if(url.contains("%") || url.contains("\\") || url.contains("/../") || url.contains("/./")) throw new BusinessException(400,"文件路径格式无效");
        response.setHeader("Cache-Control","no-store");response.setHeader("Vary","Authorization");
        Long owner=bindings.captureLegacy(url);
        if(owner!=null && owner!=0) access.requireRead(docs.selectById(owner),access.actor());
        return true;
    }
}
