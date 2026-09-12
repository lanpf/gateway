package com.cloud.gateway.application.policy;

public record SaveAppPolicyCommand(String expectedSourceRevision, AppPolicy policy) { }
