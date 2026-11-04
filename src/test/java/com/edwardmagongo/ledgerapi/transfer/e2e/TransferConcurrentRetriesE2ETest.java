package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import com.edwardmagongo.ledgerapi.support.ConcurrentRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertAllOthersWereToldKeyIsInFlight;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertAllSuccessesAreTheSameTransfer;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrentRetriesE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
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
}
