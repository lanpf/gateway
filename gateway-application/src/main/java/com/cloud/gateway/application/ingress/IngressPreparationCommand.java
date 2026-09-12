package com.cloud.gateway.application.ingress;

public record IngressPreparationCommand(String method, String path, String remoteIp) { }
