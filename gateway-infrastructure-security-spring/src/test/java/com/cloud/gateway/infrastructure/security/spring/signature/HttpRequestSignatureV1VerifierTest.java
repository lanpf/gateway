package com.cloud.gateway.infrastructure.security.spring.signature;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.application.security.RequestSignatureAuthenticationCommandService;
import com.cloud.gateway.domain.security.signature.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HttpRequestSignatureV1VerifierTest {
    private static final String SECRET = "test-secret";
    private static final String SIGNATURE = "687dceb07181813df893c9c67cc7961dad20cafed72fb563eec02601fd2fcda1";
    private static final String[] FIELDS = {
            "1777777777000", SIGNATURE, "POST", "/v1/%E4%B8%AD", "a=1&a=2",
            "app-a", "client-a", "active", "abcdefghijklmnopqrstuv"};
    private final HttpRequestSignatureV1Verifier verifier = new HttpRequestSignatureV1Verifier(new RequestSignatureCryptography());

    @Test
    void shouldMatchIndependentFixedVector() {
        String canonical = "25:HTTP_REQUEST_SIGNATURE_V1,4:POST,13:/v1/%E4%B8%AD,7:a=1&a=2,"
                + "5:app-a,8:client-a,6:active,13:1777777777000,22:abcdefghijklmnopqrstuv,"
                + "64:239f59ed55e737c77147cf55ad0c1b030b6d7ee748a7426952f9b852d5a935e5,";
        assertEquals(canonical, new String(HttpRequestSignatureV1Verifier.canonicalPayload(evidence(FIELDS), RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256, evidence(FIELDS).payload())),
                StandardCharsets.UTF_8));
        assertTrue(verifier.verify(SECRET, evidence(FIELDS)));
    }

    @Test
    void shouldRejectTamperingWithEveryCoveredFieldAndBody() {
        for (int field = 0; field < FIELDS.length; field++) {
            String[] changed = FIELDS.clone();
            changed[field] += "x";
            assertFalse(verifier.verify(SECRET, evidence(changed)), "field " + field);
        }
        ClientSignatureEvidence original = evidence(FIELDS);
        ClientSignatureEvidence changedBody = new ClientSignatureEvidence(new byte[]{0, (byte) 0xff}, original.timestamp(),
                original.signature(), original.method(), original.rawPath(), original.rawQuery(), original.appId(),
                original.clientId(), original.credentialId(), original.nonce());
        assertFalse(verifier.verify(SECRET, changedBody));
        assertFalse(verifier.verify("different-secret", original));
    }

    @Test
    void shouldPreserveRawQueryOrderAndDistinguishAbsentQuery() {
        String[] reordered = FIELDS.clone();
        reordered[4] = "a=2&a=1";
        assertFalse(verifier.verify(SECRET, evidence(reordered)));
        String[] absent = FIELDS.clone();
        absent[4] = null;
        String[] empty = FIELDS.clone();
        empty[4] = "";
        assertFalse(Arrays.equals(HttpRequestSignatureV1Verifier.canonicalPayload(evidence(absent), RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256, evidence(absent).payload())),
                HttpRequestSignatureV1Verifier.canonicalPayload(evidence(empty), RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256, evidence(empty).payload()))));
    }

    @Test
    void shouldRejectNonCanonicalOrMissingFieldsEvenWhenResigned() {
        Object[][] invalid = {{0, "+1777777777000"}, {0, "01777777777000"}, {2, "PO ST"},
                {3, "/invalid%"}, {3, "/path?query"}, {4, "a=#fragment"}, {5, "app\n"},
                {6, null}, {7, ""}, {8, "short"}, {8, "abcdefghijklmnopqrstuv="}};
        for (Object[] change : invalid) {
            String[] fields = FIELDS.clone();
            fields[(Integer) change[0]] = (String) change[1];
            fields[1] = new RequestSignatureCryptography().hmacSha256(SECRET,
                    HttpRequestSignatureV1Verifier.canonicalPayload(evidence(fields), RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256, evidence(fields).payload())));
            assertFalse(verifier.verify(SECRET, evidence(fields)));
        }
        String[] upperSignature = FIELDS.clone();
        upperSignature[1] = SIGNATURE.toUpperCase(java.util.Locale.ROOT);
        assertFalse(verifier.verify(SECRET, evidence(upperSignature)));
        assertFalse(verifier.verify(SECRET, new ClientSignatureEvidence(new byte[0], FIELDS[0], SIGNATURE)));
    }

    @Test
    void shouldDefensivelyCopyBodyAndRedactEvidence() {
        byte[] body = new byte[]{1, 2};
        ClientSignatureEvidence evidence = new ClientSignatureEvidence(body, FIELDS[0], SIGNATURE);
        body[0] = 3;
        evidence.payload()[1] = 4;
        assertArrayEquals(new byte[]{1, 2}, evidence.payload());
        assertEquals("ClientSignatureEvidence[redacted]", evidence.toString());
    }

    private static ClientSignatureEvidence evidence(String[] fields) {
        return new ClientSignatureEvidence("payload".getBytes(StandardCharsets.UTF_8), fields[0], fields[1],
                fields[2], fields[3], fields[4], fields[5], fields[6], fields[7], fields[8]);
    }
}
