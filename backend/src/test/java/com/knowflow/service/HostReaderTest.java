package com.knowflow.service;
import com.knowflow.dto.LocalReaderResolveDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HostReaderTest {
    @TempDir Path temp;
    LocalReaderService reader; HostCapabilityGuard guard; Path root,outside;
    @BeforeEach void setup() throws Exception {
        root=Files.createDirectory(temp.resolve("allowed")); outside=Files.createDirectory(temp.resolve("outside"));
        Files.writeString(root.resolve("fixture.md"),"safe fixture"); Files.write(outside.resolve("image.png"),new byte[]{1,2});
        HostCapabilityProperties props=new HostCapabilityProperties(); props.setEnabled(true); props.setAuthorizedUserIds(List.of(1L)); props.setAllowedRoots(List.of(root.toString()));
        guard=new HostCapabilityGuard(props); reader=new LocalReaderService(guard);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"unused",List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void canReadAllowedFile() { assertEquals("safe fixture",reader.readDocContent(root.toString(),"fixture.md")); }
    @Test void contentTraversal403() { BusinessException e=assertThrows(BusinessException.class,()->reader.readDocContent(root.toString(),"../outside/image.png")); assertEquals(403,e.getCode()); }
    @Test void scanDoesNotFollowExternalSymlink() throws Exception { Files.createSymbolicLink(root.resolve("external"),outside); assertEquals(403,assertThrows(BusinessException.class,()->reader.scanDirectory(root.toString())).getCode()); }
    @Test void imageFallbackCannotHideEscape() throws Exception { Files.createSymbolicLink(root.resolve("external"),outside); assertEquals(403,assertThrows(BusinessException.class,()->reader.findImage(root.toString(),"external/image.png","")).getCode()); }
    @Test void imageAbsoluteEscape403() { assertEquals(403,assertThrows(BusinessException.class,()->reader.findImage(root.toString(),outside.resolve("image.png").toString(),"")).getCode()); }
    @Test void missingImageReturnsNull() { assertNull(reader.findImage(root.toString(),"missing.png","")); }
    @Test void importCollectionRejectsExternalSymlink() throws Exception {
        Files.createSymbolicLink(root.resolve("external"),outside);
        assertEquals(403,assertThrows(BusinessException.class,()->new PathImportService(reader,guard).collectFiles(root.toString())).getCode());
    }
    @Test void lazyImportReadRechecksReplacedFile() throws Exception {
        var files=new PathImportService(reader,guard).collectFiles(root.toString());
        assertEquals(1,files.length);
        Files.delete(root.resolve("fixture.md"));
        Files.createSymbolicLink(root.resolve("fixture.md"),outside.resolve("image.png"));
        assertEquals(403,assertThrows(BusinessException.class,()->files[0].getBytes()).getCode());
    }
    @Test void invalidImagePathIs400() { assertEquals(400,assertThrows(BusinessException.class,()->reader.findImage(root.toString(),null,null)).getCode()); }
    @Test void canonicalRootCanBeReused() throws Exception {
        Path alias=temp.resolve("alias"); Files.createSymbolicLink(alias,root);
        HostCapabilityProperties props=new HostCapabilityProperties(); props.setEnabled(true); props.setAuthorizedUserIds(List.of(1L)); props.setAllowedRoots(List.of(alias.toString()));
        LocalReaderService viaAlias=new LocalReaderService(new HostCapabilityGuard(props)); LocalReaderResolveDTO dto=new LocalReaderResolveDTO();dto.setPath(alias.toString());
        assertEquals("safe fixture",viaAlias.readDocContent(viaAlias.resolvePath(dto),"fixture.md"));
    }
}
