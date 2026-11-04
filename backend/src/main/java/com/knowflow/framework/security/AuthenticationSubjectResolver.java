package com.knowflow.framework.security;

import java.util.List;
import java.util.Optional;

/** 读取当前可认证主体及其最新权限。 */
public interface AuthenticationSubjectResolver {
  Optional<Subject> resolve(long userId);

  record Subject(long userId, List<String> authorities) {
    public Subject {
      authorities = List.copyOf(authorities);
    }
  }
}
