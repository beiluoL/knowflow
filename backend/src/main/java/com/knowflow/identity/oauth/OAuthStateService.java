package com.knowflow.identity.oauth;

import com.knowflow.identity.mapper.OAuthStateMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 签发绑定浏览器的短期 OAuth 状态，并且只允许消费一次。 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {
  public static final Duration TTL = Duration.ofMinutes(5);
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
  private static final SecureRandom RANDOM = new SecureRandom();
  private final OAuthStateMapper states;

  /** 为供应商签发新的状态及独立的浏览器绑定值。 */
  public IssuedState issue(String provider) {
    long now = System.currentTimeMillis();
    String state = randomToken();
    String browserBinding = randomToken();
    states.deleteExpired(now);
    states.insertState(hash(state, browserBinding), provider, now + TTL.toMillis());
    return new IssuedState(state, browserBinding);
  }

  /** 校验供应商和浏览器绑定，成功时原子删除状态。 */
  public boolean consume(String provider, String state, String browserBinding) {
    return state != null
        && browserBinding != null
        && TOKEN.matcher(state).matches()
        && TOKEN.matcher(browserBinding).matches()
        && states.consume(hash(state, browserBinding), provider, System.currentTimeMillis()) == 1;
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String state, String binding) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((state + "." + binding).getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  public record IssuedState(String state, String browserBinding) {}
}
