package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.gateway.api.facade.admin.definition.SaveCapabilityPolicyConfigApiCommand;
import com.cloud.gateway.application.configuration.editing.SaveCapabilityPolicyConfigCommand;

/** Registered capability payloads to the existing content-only save command. */
public interface CapabilityPolicyConfigApiMapper {
    SaveCapabilityPolicyConfigCommand toCommand(SaveCapabilityPolicyConfigApiCommand source);
}
