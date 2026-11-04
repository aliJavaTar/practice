package com.edwardmagongo.ledgerapi.transfer.e2e;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.edwardmagongo.ledgerapi.support.LedgerApi.json;
import static org.assertj.core.api.Assertions.assertThat;

/** The assertions shared by the transfer end-to-end tests. */
final class TransferAssertions {

    private TransferAssertions() {
    }

    static void assertCreated(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as("body: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    static void assertError(ResponseEntity<String> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).as("body: %s", response.getBody()).isEqualTo(status);
        assertThat(json(response).get("message").asText()).isEqualTo(message);
    }

    static void assertKeyReused(ResponseEntity<String> response) {
        assertError(response, HttpStatus.CONFLICT,
                "This Idempotency-Key was already used with different request parameters");
    }

    static void assertKeyInFlight(ResponseEntity<String> response) {
        assertError(response, HttpStatus.CONFLICT,
                "A request with this Idempotency-Key is already in progress; retry shortly");
    }

    static void assertBalances(TransferFixture fixture, String alice, String bob) {
        assertThat(fixture.api.balance(fixture.alice, fixture.aliceAccount))
                .as("alice's balance").isEqualByComparingTo(alice);
        assertThat(fixture.api.balance(fixture.bob, fixture.bobAccount))
                .as("bob's balance").isEqualByComparingTo(bob);
    }

    /** Proves a key is free by using it. Expects Alice to still hold 500.00 and Bob nothing. */
    static void assertKeyIsStillUsable(TransferFixture fixture, String key) {
        assertCreated(fixture.transferFromAliceToBob(key, "100.00"));
        assertBalances(fixture, "400.00", "100.00");
    }

    static void assertAllSuccessesAreTheSameTransfer(List<ResponseEntity<String>> responses) {
        assertThat(responses.stream().filter(TransferAssertions::isCreated).map(ResponseEntity::getBody).distinct())
                .as("every success is the same transfer, executed or replayed")
                .hasSize(1);
    }

    static void assertAllOthersWereToldKeyIsInFlight(List<ResponseEntity<String>> responses) {
        assertThat(responses.stream().filter(response -> !isCreated(response)))
                .allSatisfy(TransferAssertions::assertKeyInFlight);
    }

    private static boolean isCreated(ResponseEntity<String> response) {
        return response.getStatusCode().isSameCodeAs(HttpStatus.CREATED);
    }
}
