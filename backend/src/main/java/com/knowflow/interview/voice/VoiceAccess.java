package com.knowflow.interview.voice;

import com.knowflow.exception.BusinessException;
import com.knowflow.interview.speech.InterviewStreamingAsrProvider;
import com.knowflow.interview.speech.InterviewTtsProvider;
import com.knowflow.interview.v2.InterviewV2Service;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 实时能力只由真实Provider配置决定，不把整段上传冒充实时流。 */
@Service
@RequiredArgsConstructor
public class VoiceAccess {

  private final InterviewV2Service interviews;
  private final VoiceTicketStore tickets;
  private final VoiceTransportProperties config;
  private final InterviewStreamingAsrProvider asr;
  private final InterviewTtsProvider tts;

  public Map<String, Object> state(long user, long session) {
    return interviews.get(user, session);
  }

  public Map<String, Object> capability(long user, long session) {
    var state = state(user, session);
    boolean active = !"COMPLETED".equals(state.get("state"));
    boolean enabled =
      config.isEnabled() &&
      asr.capabilities().available() &&
      !config.validOrigins().isEmpty() &&
      active;
    return Map.of(
      "enabled",
      enabled,
      "asr",
      asr.capabilities(),
      "tts",
      tts.capabilities(),
      "protocolVersion",
      1,
      "maxDurationSeconds",
      60,
      "maxBytes",
      1920000,
      "llmStreaming",
      interviews.streamingAvailable(user),
      "reason",
      enabled
        ? interviews.streamingAvailable(user)
          ? "实时候选追问仅供预览，校验完成后由原V2发布正式问题"
          : "流式模型未配置；追问经原V2校验后完整发送"
        : "实时语音未配置、来源未允许或面试已结束；仍可使用原录音转写和文字作答"
    );
  }

  public Map<String, Object> ticket(long user, long session, String origin) {
    if (
      !Boolean.TRUE.equals(capability(user, session).get("enabled"))
    ) throw new BusinessException(503, "实时语音未启用或未配置");
    if (!config.allows(origin)) throw new BusinessException(
      403,
      "语音来源未允许"
    );
    return Map.of(
      "ticket",
      tickets.issue(user, session, origin, config.getMaxConnectionsPerUser()),
      "expiresInSeconds",
      15,
      "protocolVersion",
      1
    );
  }

  public VoiceTicketStore.Identity consume(
    String ticket,
    long session,
    String origin
  ) {
    if (
      !config.isEnabled() ||
      !config.allows(origin) ||
      !asr.capabilities().available()
    ) throw new BusinessException(403, "实时语音来源或能力不可用");
    var identity = tickets.consume(ticket, session, origin);
    try {
      if (
        "COMPLETED".equals(state(identity.user(), session).get("state"))
      ) throw new BusinessException(409, "面试已结束");
      return identity;
    } catch (RuntimeException invalid) {
      tickets.close(identity);
      throw invalid;
    }
  }
}
