package com.knowflow.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** 显式配置部署信任，与业务角色和工具开关相互独立。 */
@Getter @Setter @Component
@ConfigurationProperties(prefix="knowflow.host-capabilities")
public class HostCapabilityProperties {
    private boolean enabled = false;
    private List<Long> authorizedUserIds = new ArrayList<>();
    private List<String> allowedRoots = new ArrayList<>();
}
