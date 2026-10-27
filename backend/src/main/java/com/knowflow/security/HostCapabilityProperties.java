package com.knowflow.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Explicit deployment trust, independent from business roles and tool toggles. */
@Getter @Setter @Component
@ConfigurationProperties(prefix="knowflow.host-capabilities")
public class HostCapabilityProperties {
    private boolean enabled = false;
    private List<Long> authorizedUserIds = new ArrayList<>();
    private List<String> allowedRoots = new ArrayList<>();
}
