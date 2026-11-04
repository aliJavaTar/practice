package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.common.idempotency.IdempotentOperation;
import com.edwardmagongo.ledgerapi.common.idempotency.RequestFingerprint;
import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyInFlight;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;

/**
 * A request still running cannot be held open deterministically over HTTP, so this is the one test
 * that writes to the database directly: it inserts the claim such a request would hold.
 */
class TransferInProgressE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldRejectRequestWhileOriginalWithSameKeyIsStillInProgress() {
        String key = newKey();
        insertInProgressClaim(key);

        ResponseEntity<String> response = fixture.transferFromAliceToBob(key, "100.00");

        assertKeyInFlight(response);
        assertBalances(fixture, "500.00", "0.00");
    }

    private void insertInProgressClaim(String key) {
        String fingerprint = RequestFingerprint.of(IdempotentOperation.TRANSFER,
                fixture.aliceAccount, fixture.bobAccount, "100");
        jdbc.update("""
                INSERT INTO idempotency_keys
                    (id, idempotency_key, user_id, operation, request_fingerprint, status, created_at)
                VALUES (?, ?, ?, 'TRANSFER', ?, 'IN_PROGRESS', now())""",
                UUID.randomUUID(), key, fixture.api.userId(fixture.alice), fingerprint);
    }
}
