package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.framework.core.SubjectType;
import jakarta.validation.Valid;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

/** JavaBean binding containers; conversion and domain validation occur before publication. */
@Getter
public final class CapabilityPolicyConfigProperties {
    @Valid private final Map<String, Bundle> bundles = new LinkedHashMap<>();
    @Valid private final Map<String, Binding> bindings = new LinkedHashMap<>();

    @Getter @Setter
    public static final class Bundle {
        @Valid private String bundleId;
        @Valid private final Map<String, Map<String, Object>> capabilities = new LinkedHashMap<>();
    }

    @Getter @Setter
    public static final class Binding {
        @Valid private String bindingId;
        @Valid private String bundleId;
        @Valid private String serviceId;
        @Valid private String endpointId;
        @Valid private final Set<String> tags = new LinkedHashSet<>();
        @Valid private String appId;
        @Valid private String verifiedClientId;
        @Valid private SubjectType subjectType;
    }
}
