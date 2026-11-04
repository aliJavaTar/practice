package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static com.edwardmagongo.ledgerapi.support.LedgerApi.json;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertCreated;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyInFlight;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

class TransferWithKeyE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldMoveMoneyAndReturnTransferDetails() {
        ResponseEntity<String> response = fixture.transferFromAliceToBob(newKey(), "100.00");

        assertCreated(response);
        JsonNode body = json(response);
        assertThat(body.get("transferId").asText()).isNotBlank();
        assertThat(body.get("fromAccountId").asText()).isEqualTo(fixture.aliceAccount);
        assertThat(body.get("toAccountId").asText()).isEqualTo(fixture.bobAccount);
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(body.get("fromBalanceAfter").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(body.get("toBalanceAfter").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(body.get("createdAt").asText()).isNotBlank();
        assertBalances(fixture, "400.00", "100.00");
    }

    @Test
    void shouldRecordBothLedgerLegsUnderOneTransferId() {
        String transferId = json(fixture.transferFromAliceToBob(newKey(), "100.00")).get("transferId").asText();

        JsonNode out = fixture.api.transactions(fixture.alice, fixture.aliceAccount).getFirst();
        JsonNode in = fixture.api.transactions(fixture.bob, fixture.bobAccount).getFirst();
        assertThat(out.get("type").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(out.get("transferId").asText()).isEqualTo(transferId);
        assertThat(out.get("balanceAfter").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(in.get("type").asText()).isEqualTo("TRANSFER_IN");
        assertThat(in.get("transferId").asText()).isEqualTo(transferId);
        assertThat(in.get("balanceAfter").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldTransferTheSmallestAllowedAmount() {
        assertCreated(fixture.transferFromAliceToBob(newKey(), "0.01"));

        assertBalances(fixture, "499.99", "0.01");
    }

    @Test
    void shouldTransferTheEntireBalance() {
        assertCreated(fixture.transferFromAliceToBob(newKey(), "500.00"));

        assertBalances(fixture, "0.00", "500.00");
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

    @Test
    void shouldAcceptKeyOf255Characters() {
        assertCreated(fixture.transferFromAliceToBob("k".repeat(255), "100.00"));

        assertBalances(fixture, "400.00", "100.00");
    }

    /** Known bug, pinned as it behaves today. The correct response is 400; change this test when fixed. */
    @Test
    void shouldCurrentlyRespondInFlightForKeyLongerThan255Characters() {
        assertKeyInFlight(fixture.transferFromAliceToBob("k".repeat(256), "100.00"));

        assertBalances(fixture, "500.00", "0.00");
    }
}
