package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static com.edwardmagongo.ledgerapi.support.LedgerApi.json;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertCreated;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

class TransferKeyScopeE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldTreatSameKeyFromDifferentUsersAsIndependentRequests() {
        String sharedKey = newKey();
        fixture.api.deposit(fixture.bob, fixture.bobAccount, "300.00");

        ResponseEntity<String> fromAlice = fixture.transferFromAliceToBob(sharedKey, "100.00");
        ResponseEntity<String> fromBob =
                fixture.api.transfer(fixture.bob, sharedKey, fixture.bobAccount, fixture.aliceAccount, "50.00");

        assertCreated(fromAlice);
        assertCreated(fromBob);
        assertThat(json(fromBob).get("transferId")).isNotEqualTo(json(fromAlice).get("transferId"));
        assertBalances(fixture, "450.00", "350.00");
    }

    @Test
    void shouldMoveMoneyTwiceWhenSameRequestIsSentTwiceWithoutKey() {
        ResponseEntity<String> first = fixture.transferFromAliceToBob(null, "100.00");
        ResponseEntity<String> second = fixture.transferFromAliceToBob(null, "100.00");

        assertCreated(first);
        assertCreated(second);
        assertThat(json(second).get("transferId")).isNotEqualTo(json(first).get("transferId"));
        assertBalances(fixture, "300.00", "200.00");
    }
}
