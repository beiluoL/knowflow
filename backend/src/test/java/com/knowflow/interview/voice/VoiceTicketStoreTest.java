package com.knowflow.interview.voice;

import com.knowflow.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.junit.jupiter.api.Assertions.*;

class VoiceTicketStoreTest {
    private static final String ORIGIN = "http://localhost:5173";
    private JdbcTemplate jdbc;
    private VoiceTicketStore store;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:voice_ticket_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,deleted INT NOT NULL)");
        jdbc.update("INSERT INTO sys_user VALUES(7,0),(8,0),(9,1)");
        store = new VoiceTicketStore(jdbc, new DataSourceTransactionManager(source));
        store.initialize();
    }

    @Test
    void storesOnlyDigestAndAllowsExactlyOneConsumption() {
        String ticket = store.issue(7L, 11L, ORIGIN, 2);
        assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"));
        String stored = jdbc.queryForObject("SELECT ticket_hash FROM interview_voice_ticket", String.class);
        assertEquals(VoiceTicketStore.hash(ticket), stored);
        assertNotEquals(ticket, stored);
        var identity = store.consume(ticket, 11L, ORIGIN);
        assertEquals(7L, identity.user());
        assertEquals(11L, identity.session());
        assertEquals(stored, identity.ticketHash());
        assertCode(401, () -> store.consume(ticket, 11L, ORIGIN));
    }

    @Test
    void wrongOriginOrSessionDoesNotConsumeValidTicket() {
        String ticket = store.issue(7L, 11L, ORIGIN, 2);
        assertCode(401, () -> store.consume(ticket, 12L, ORIGIN));
        assertCode(401, () -> store.consume(ticket, 11L, "http://attacker.invalid"));
        assertEquals(7L, store.consume(ticket, 11L, ORIGIN).user());
        assertCode(401, () -> store.consume("invalid", 11L, ORIGIN));
    }

    @Test
    void expirationRejectsConsumptionAndRenewalWithoutSleeping() {
        String expired = store.issue(7L, 11L, ORIGIN, 2);
        expire(expired);
        assertCode(401, () -> store.consume(expired, 11L, ORIGIN));
        String active = store.issue(7L, 11L, ORIGIN, 2);
        var identity = store.consume(active, 11L, ORIGIN);
        expire(active);
        assertFalse(store.renew(identity));
    }

    @Test
    void userQuotaIncludesIssuedAndConnectedAndCloseReleasesSlot() {
        String first = store.issue(7L, 11L, ORIGIN, 2);
        var identity = store.consume(first, 11L, ORIGIN);
        store.issue(7L, 12L, ORIGIN, 2);
        assertCode(429, () -> store.issue(7L, 13L, ORIGIN, 2));
        assertNotNull(store.issue(8L, 11L, ORIGIN, 2));
        assertTrue(store.renew(identity));
        store.close(identity);
        assertFalse(store.renew(identity));
        assertNotNull(store.issue(7L, 13L, ORIGIN, 2));
        assertCode(401, () -> store.issue(9L, 11L, ORIGIN, 2));
        assertCode(401, () -> store.issue(999L, 11L, ORIGIN, 2));
    }

    @Test
    void closedTicketsStillCountTowardPerMinuteIssueBudget() {
        for (int index = 0; index < 10; index++) {
            String ticket = store.issue(7L, 11L, ORIGIN, 2);
            store.close(store.consume(ticket, 11L, ORIGIN));
        }
        assertCode(429, () -> store.issue(7L, 11L, ORIGIN, 2));
    }

    @Test
    void simultaneousConsumptionHasExactlyOneWinner() throws Exception {
        String ticket = store.issue(7L, 11L, ORIGIN, 2);
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.initialize();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> consume = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try {
                store.consume(ticket, 11L, ORIGIN);
                return true;
            } catch (BusinessException exception) {
                assertEquals(401, exception.getCode());
                return false;
            }
        };
        try {
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1L, List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count());
        } finally {
            start.countDown();
            executor.shutdown();
        }
    }

    private void expire(String ticket) {
        jdbc.update("UPDATE interview_voice_ticket SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) "
                + "WHERE ticket_hash=?", VoiceTicketStore.hash(ticket));
    }

    private void assertCode(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(BusinessException.class, operation).getCode());
    }
}
