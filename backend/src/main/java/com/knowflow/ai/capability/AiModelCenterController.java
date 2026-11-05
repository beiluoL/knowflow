package com.knowflow.ai.capability;

import com.knowflow.common.Result;
import com.knowflow.config.AiProviderRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员只读模型目录和当前实例的计量概览。 */
@RestController
@RequestMapping("/api/admin/ai-model-center")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiModelCenterController {
  private final AiProviderRegistry providers;
  private final AiCapabilityExecutor executor;

  @GetMapping
  public Result<Overview> overview() {
    List<ModelEntry> models =
        providers.all().stream()
            .flatMap(
                provider -> {
                  var popular = provider.getPopularModels();
                  Stream<String> advertised =
                      popular == null ? Stream.empty() : Arrays.stream(popular);
                  return Stream.concat(Stream.ofNullable(provider.getDefaultModel()), advertised)
                      .filter(model -> model != null && !model.isBlank())
                      .distinct()
                      .map(
                          model ->
                              new ModelEntry(
                                  provider.getProvider(),
                                  provider.getLabel(),
                                  model,
                                  provider.getType().name(),
                                  model.equals(provider.getDefaultModel())));
                })
            .toList();
    return Result.success(new Overview(models, executor.usageSnapshot()));
  }

  public record ModelEntry(
      String provider, String label, String model, String providerType, boolean defaultModel) {}

  public record Overview(List<ModelEntry> models, AiCapabilityExecutor.UsageSnapshot usage) {}
}
