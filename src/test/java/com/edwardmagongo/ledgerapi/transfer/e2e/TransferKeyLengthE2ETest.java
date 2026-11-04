package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertCreated;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertKeyInFlight;

class TransferKeyLengthE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
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
