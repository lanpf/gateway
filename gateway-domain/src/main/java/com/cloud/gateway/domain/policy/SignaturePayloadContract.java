package com.cloud.gateway.domain.policy;
public enum SignaturePayloadContract {
    LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER,
    LEGACY_SECRET_TIMESTAMP_BODY_SHA256,
    SECRET_TIMESTAMP_BODY_HMAC_SHA256
}
