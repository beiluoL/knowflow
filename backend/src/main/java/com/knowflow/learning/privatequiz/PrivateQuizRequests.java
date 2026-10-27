package com.knowflow.learning.privatequiz;

import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

public final class PrivateQuizRequests {
 private PrivateQuizRequests() {}
 public record Preview(@NotNull @Positive Long noteId,@NotNull @Positive Long expectedRevision,
                       @NotBlank @Size(min=8,max=80) String requestId) {}
 public record Question(@NotBlank @Pattern(regexp="TRUE_FALSE|SINGLE_CHOICE") String type,
                        @NotBlank @Size(max=3000) String content,
                        @NotNull @Size(max=6) List<@NotBlank @Size(max=1000) String> options,
                        @NotBlank @Size(max=10) String answer,
                        @NotBlank @Size(max=3000) String explanation) {}
 public record Confirm(@NotNull @Size(min=3,max=3) List<@NotNull @Valid Question> questions) {}
 public record Begin(@NotBlank @Size(min=8,max=80) String requestId) {}
 public record Submit(@NotNull @Size(min=3,max=3) List<@NotNull @Size(max=10) String> answers) {}
}
