package com.knowflow.service;
import com.knowflow.service.impl.CodeWorkspaceServiceImpl;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HostOwnedWorkspaceTest {
    @TempDir Path temp;
    @Test void normalOwnedFileStillWorks() {
        CodeWorkspaceServiceImpl service=new CodeWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service,"workspaceRoot",temp.resolve("workspaces").toString());
        assertEquals("safe",service.saveFile(1L,"safe.py","safe").getContent());
    }
    @Test void listCannotReadExternalFixtureViaSymlink() throws Exception {
        CodeWorkspaceServiceImpl service=new CodeWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service,"workspaceRoot",temp.resolve("workspaces").toString());
        Path root=service.getWorkspaceDir(1L);
        Path fixture=Files.writeString(temp.resolve("outside.py"),"safe outside fixture");
        Files.createSymbolicLink(root.resolve("link.py"),fixture);
        assertEquals(403,assertThrows(BusinessException.class,()->service.listFiles(1L)).getCode());
    }
    @Test void saveCannotWriteExternalFixtureViaSymlink() throws Exception {
        CodeWorkspaceServiceImpl service=new CodeWorkspaceServiceImpl();
        ReflectionTestUtils.setField(service,"workspaceRoot",temp.resolve("workspaces").toString());
        Path root=service.getWorkspaceDir(1L);
        Path fixture=Files.writeString(temp.resolve("outside.py"),"safe outside fixture");
        Files.createSymbolicLink(root.resolve("link.py"),fixture);
        assertEquals(403,assertThrows(BusinessException.class,()->service.saveFile(1L,"link.py","replacement")).getCode());
        assertEquals("safe outside fixture",Files.readString(fixture));
    }
}
