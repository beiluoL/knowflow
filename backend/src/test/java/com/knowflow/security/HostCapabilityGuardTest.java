package com.knowflow.security;

import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HostCapabilityGuardTest {
    @TempDir Path temp;
    HostCapabilityProperties props;
    HostCapabilityGuard guard;
    Path root,outside;
    @BeforeEach void setup() throws Exception {
        props=new HostCapabilityProperties(); guard=new HostCapabilityGuard(props);
        root=Files.createDirectory(temp.resolve("allowed")); outside=Files.createDirectory(temp.resolve("outside"));
        Files.writeString(root.resolve("fixture.md"),"safe"); Files.writeString(outside.resolve("outside.md"),"outside fixture");
        props.setEnabled(true); props.setAuthorizedUserIds(List.of(1L)); props.setAllowedRoots(List.of(root.toString())); actor(1L,"USER");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void actor(Long id,String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role)))); }
    void rejects(int code,Runnable action) { BusinessException e=assertThrows(BusinessException.class,action::run); assertEquals(code,e.getCode()); assertFalse(e.getMessage().contains(temp.toString())); }
    @Test void defaultsClosedEvenForAdmin() { props=new HostCapabilityProperties(); guard=new HostCapabilityGuard(props); actor(1L,"ADMIN"); rejects(403,()->guard.requireAccess()); }
    @Test void enabledDoesNotAuthorizeAdminNotOnList() { actor(2L,"ADMIN"); rejects(403,()->guard.requireAccess()); }
    @Test void anonymousDenied() { clear(); rejects(401,()->guard.requireAccess()); }
    @Test void suppliedToolActorCannotForgeIdentity() { rejects(403,()->guard.requireActor(2L)); }
    @Test void relativeUsesServerRootNotJvmDirectory() throws Exception { assertEquals(root.resolve("fixture.md").toRealPath(),guard.resolveAllowed("fixture.md",null)); }
    @Test void clientCannotRedefineRoot() { rejects(403,()->guard.resolveAllowed("outside.md",outside.toString())); }
    @Test void traversalRejected() { rejects(403,()->guard.resolveAllowed("../outside/outside.md",root.toString())); }
    @Test void absoluteEscapeRejected() { rejects(403,()->guard.resolveAllowed(outside.resolve("outside.md").toString(),null)); }
    @Test void missingOutsideDoesNotProbeExistence() { rejects(403,()->guard.resolveAllowed(outside.resolve("missing.md").toString(),null)); }
    @Test void missingInside404() { rejects(404,()->guard.resolveAllowed(root.resolve("missing.md").toString(),null)); }
    @Test void symlinkEscapeRejected() throws Exception { Files.createSymbolicLink(root.resolve("link"),outside); rejects(403,()->guard.resolveAllowed("link/outside.md",root.toString())); }
    @Test void missingUnderEscapingSymlinkRejected() throws Exception { Files.createSymbolicLink(root.resolve("link"),outside); rejects(403,()->guard.resolveAllowed("link/missing.md",root.toString())); }
    @Test void emptyRootsClosed() { props.setAllowedRoots(List.of()); rejects(403,()->guard.resolveAllowed("fixture.md",null)); }
    @Test void timeoutCannotBeExtended() { rejects(400,()->guard.executionTimeout(10001L,10000)); }
    @Test void timeoutCannotBeNonpositive() { rejects(400,()->guard.executionTimeout(0L,10000)); rejects(400,()->guard.executionTimeout(-1L,10000)); }
    @Test void fallbackIsCapped() { assertEquals(10000L,guard.executionTimeout(null,60000)); }
    @Test void asyncSnapshotWorksAndCannotLeakToNextTask() throws Exception {
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try { Runnable work=guard.wrapAuthenticated(()->guard.requireActor(1L)); clear(); pool.submit(work).get(); assertEquals(401,pool.submit(()->{try{guard.requireAccess();return 200;}catch(BusinessException e){return e.getCode();}}).get()); }
        finally {pool.shutdownNow();}
    }
    @Test void workspaceSymlinkEscapeRejected() throws Exception { Files.createSymbolicLink(root.resolve("link"),outside); rejects(403,()->guard.requireWorkspace(root,root.resolve("link/new.md"))); }
    @Test void concurrencyHasBoundedQueueAndReleasesPermits() {
        java.util.List<HostCapabilityGuard.ProcessLease> leases=new java.util.ArrayList<>();
        try { for(int i=0;i<8;i++) leases.add(guard.acquireProcess(1)); rejects(409,()->guard.acquireProcess(1)); }
        finally { leases.forEach(HostCapabilityGuard.ProcessLease::close); }
        try(var lease=guard.acquireProcess(1)) { assertNotNull(lease); }
    }
}
