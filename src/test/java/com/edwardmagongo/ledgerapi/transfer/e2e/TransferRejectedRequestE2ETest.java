package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static com.edwardmagongo.ledgerapi.support.LedgerApi.json;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertError;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyIsStillUsable;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

class TransferRejectedRequestE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldNotConsumeKeyWhenAmountIsZero() {
        String key = newKey();

        ResponseEntity<String> invalid = fixture.transferFromAliceToBob(key, "0.00");

        assertError(invalid, HttpStatus.BAD_REQUEST, "Validation failed");
        assertThat(json(invalid).get("fieldErrors").get(0).get("field").asText()).isEqualTo("amount");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldNotConsumeKeyWhenAmountHasMoreThanTwoDecimalPlaces() {
        String key = newKey();

        ResponseEntity<String> invalid = fixture.transferFromAliceToBob(key, "100.001");

        assertError(invalid, HttpStatus.BAD_REQUEST, "Validation failed");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldNotConsumeKeyWhenRequestIsUnauthenticated() {
        String key = newKey();

        ResponseEntity<String> response =
                fixture.api.transfer(null, key, fixture.aliceAccount, fixture.bobAccount, "100.00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertBalances(fixture, "500.00", "0.00");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldRejectBlankKeyAndMoveNothing() {
        ResponseEntity<String> response = fixture.transferFromAliceToBob("   ", "100.00");

        assertError(response, HttpStatus.BAD_REQUEST, "Idempotency-Key must not be blank");
        assertBalances(fixture, "500.00", "0.00");
    }
}
