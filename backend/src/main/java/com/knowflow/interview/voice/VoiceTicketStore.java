package com.knowflow.interview.voice;

import com.knowflow.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 数据库原子消费短期票据；跨实例生效，连接配额以用户行锁串行分配。 */
@Component
public class VoiceTicketStore {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();

  public VoiceTicketStore(
    JdbcTemplate jdbc,
    PlatformTransactionManager manager
  ) {
    this.jdbc = jdbc;
    tx = new TransactionTemplate(manager);
  }

  @PostConstruct
  public void initialize() {
    jdbc.execute(
      (org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
        String dialect = connection
          .getMetaData()
          .getDatabaseProductName()
          .equalsIgnoreCase("H2")
          ? "h2"
          : "mysql";
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          connection,
          new org.springframework.core.io.ClassPathResource(
            "db/" + dialect + "/migration/V20261102__interview_voice_ticket.sql"
          )
        );
        return null;
      }
    );
  }

  public record Identity(long user, long session, String ticketHash) {}

  public String issue(
    long user,
    long session,
    String origin,
    int maxConnections
  ) {
    return tx.execute(status -> {
      if (
        jdbc
          .queryForList(
            "SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",
            Long.class,
            user
          )
          .isEmpty()
      ) throw new BusinessException(401, "请重新登录");
      Integer active = jdbc.queryForObject(
        "SELECT COUNT(*) FROM interview_voice_ticket WHERE user_id=? AND deleted=0 AND expires_at>CURRENT_TIMESTAMP",
        Integer.class,
        user
      );
      Integer recent = jdbc.queryForObject(
        "SELECT COUNT(*) FROM interview_voice_ticket WHERE user_id=? AND create_time>?",
        Integer.class,
        user,
        Timestamp.from(Instant.now().minusSeconds(60))
      );
      if (
        active >= Math.max(1, Math.min(2, maxConnections)) || recent >= 10
      ) throw new BusinessException(429, "语音连接过于频繁，请稍后重试");
      byte[] secret = new byte[32];
      random.nextBytes(secret);
      String ticket = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(secret);
      jdbc.update(
        "INSERT INTO interview_voice_ticket(user_id,session_id,ticket_hash,origin,status,expires_at) VALUES(?,?,?,?,'ISSUED',?)",
        user,
        session,
        hash(ticket),
        origin,
        Timestamp.from(Instant.now().plusSeconds(15))
      );
      return ticket;
    });
  }

  public Identity consume(String ticket, long session, String origin) {
    if (
      ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}") || origin == null
    ) throw new BusinessException(401, "语音连接票据无效");
    String digest = hash(ticket);
    return tx.execute(status -> {
      if (
        jdbc.update(
          "UPDATE interview_voice_ticket SET status='CONNECTED',expires_at=?,update_time=CURRENT_TIMESTAMP WHERE ticket_hash=? AND session_id=? AND origin=? AND status='ISSUED' AND deleted=0 AND expires_at>CURRENT_TIMESTAMP",
          Timestamp.from(Instant.now().plusSeconds(45)),
          digest,
          session,
          origin
        ) != 1
      ) throw new BusinessException(401, "语音连接票据已使用或过期");
      var row = jdbc.queryForMap(
        "SELECT user_id FROM interview_voice_ticket WHERE ticket_hash=?",
        digest
      );
      return new Identity(
        ((Number) row.get("user_id")).longValue(),
        session,
        digest
      );
    });
  }

  public boolean renew(Identity identity) {
    return (
      jdbc.update(
        "UPDATE interview_voice_ticket SET expires_at=?,update_time=CURRENT_TIMESTAMP WHERE ticket_hash=? AND user_id=? AND status='CONNECTED' AND deleted=0 AND expires_at>CURRENT_TIMESTAMP",
        Timestamp.from(Instant.now().plusSeconds(45)),
        identity.ticketHash(),
        identity.user()
      ) == 1
    );
  }

  public void close(Identity identity) {
    jdbc.update(
      "UPDATE interview_voice_ticket SET status='CLOSED',deleted=1,update_time=CURRENT_TIMESTAMP WHERE ticket_hash=? AND user_id=?",
      identity.ticketHash(),
      identity.user()
    );
  }

  public static String hash(String text) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          text.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
