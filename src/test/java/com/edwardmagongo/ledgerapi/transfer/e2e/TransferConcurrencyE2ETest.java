package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.common.idempotency.IdempotentOperation;
import com.edwardmagongo.ledgerapi.common.idempotency.RequestFingerprint;
import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import com.edwardmagongo.ledgerapi.support.ConcurrentRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertAllOthersWereToldKeyIsInFlight;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertAllSuccessesAreTheSameTransfer;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyInFlight;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request still running cannot be held open deterministically over HTTP, so the in-progress test
 * is the one test that writes to the database directly: it inserts the claim such a request holds.
 */
class TransferConcurrencyE2ETest extends AbstractIntegrationTest {

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

    @Test
    void shouldMoveMoneyExactlyOnceWhenManyRequestsWithSameKeyArriveTogether() throws Exception {
        String key = newKey();

        List<ResponseEntity<String>> responses =
                ConcurrentRequests.send(20, () -> fixture.transferFromAliceToBob(key, "10.00"));

        assertAllSuccessesAreTheSameTransfer(responses);
        assertAllOthersWereToldKeyIsInFlight(responses);
        assertBalances(fixture, "490.00", "10.00");
        assertThat(fixture.api.transactions(fixture.bob, fixture.bobAccount)).hasSize(1);
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
