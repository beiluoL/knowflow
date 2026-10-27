package com.knowflow.security;
import com.knowflow.agent.tool.builtin.TerminalTool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class HostInterruptedProcessTest {
    @Test void terminalInterruptedWaitStopsChildAndPreservesInterrupt() throws Exception {
        Process child=mock(Process.class);
        when(child.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(child.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(child.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(child.descendants()).thenAnswer(ignored -> Stream.empty());
        when(child.waitFor(anyLong(),any(TimeUnit.class))).thenThrow(new InterruptedException("safe stub interruption"));
        TerminalTool tool=new TerminalTool(new com.fasterxml.jackson.databind.ObjectMapper(),new HostCapabilityGuard(new HostCapabilityProperties()));
        try {
            assertThrows(Throwable.class,()->ReflectionTestUtils.invokeMethod(tool,"run",child,100L,1024));
            verify(child).destroyForcibly();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
