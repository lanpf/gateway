package com.cloud.gateway.infrastructure.policy.mapstruct;

import org.mapstruct.MapperConfig;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@MapperConfig(injectionStrategy = InjectionStrategy.CONSTRUCTOR, componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PolicyStorageMapperConfig { }
