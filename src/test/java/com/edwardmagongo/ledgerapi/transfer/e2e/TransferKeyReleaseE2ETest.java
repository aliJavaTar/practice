package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertError;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyIsStillUsable;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;

class TransferKeyReleaseE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldReleaseKeyWhenFundsAreInsufficient() {
        String key = newKey();

        ResponseEntity<String> failed = fixture.transferFromAliceToBob(key, "500.01");

        assertError(failed, HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        assertBalances(fixture, "500.00", "0.00");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenSourceAndDestinationAreTheSame() {
        String key = newKey();

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, fixture.aliceAccount, fixture.aliceAccount, "100.00");

        assertError(failed, HttpStatus.BAD_REQUEST, "Source and destination accounts must be different");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenCurrenciesDiffer() {
        String key = newKey();
        String bobDollarAccount = fixture.api.openAccount(fixture.bob, "USD");

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, fixture.aliceAccount, bobDollarAccount, "100.00");

        assertError(failed, HttpStatus.BAD_REQUEST, "Source and destination accounts must use the same currency");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenSourceAccountIsClosed() {
        String key = newKey();
        String closedAccount = fixture.api.openAccount(fixture.alice, "GBP");
        fixture.api.closeAccount(fixture.alice, closedAccount);

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, closedAccount, fixture.bobAccount, "100.00");

        assertError(failed, HttpStatus.CONFLICT, "Account is closed: " + closedAccount);
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenDestinationAccountIsClosed() {
        String key = newKey();
        String closedAccount = fixture.api.openAccount(fixture.bob, "GBP");
        fixture.api.closeAccount(fixture.bob, closedAccount);

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, fixture.aliceAccount, closedAccount, "100.00");

        assertError(failed, HttpStatus.CONFLICT, "Account is closed: " + closedAccount);
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenSourceAccountBelongsToAnotherUser() {
        String key = newKey();

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, fixture.bobAccount, fixture.aliceAccount, "100.00");

        assertError(failed, HttpStatus.FORBIDDEN, "Account does not belong to the authenticated user");
        assertKeyIsStillUsable(fixture, key);
    }

    @Test
    void shouldReleaseKeyWhenDestinationAccountDoesNotExist() {
        String key = newKey();
        String missingAccount = UUID.randomUUID().toString();

        ResponseEntity<String> failed =
                fixture.api.transfer(fixture.alice, key, fixture.aliceAccount, missingAccount, "100.00");

        assertError(failed, HttpStatus.NOT_FOUND, "Account not found: " + missingAccount);
        assertKeyIsStillUsable(fixture, key);
    }
}
