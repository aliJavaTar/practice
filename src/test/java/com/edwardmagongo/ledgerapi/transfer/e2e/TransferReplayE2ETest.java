package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static com.edwardmagongo.ledgerapi.support.LedgerApi.json;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertBalances;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferAssertions.assertCreated;
import static com.edwardmagongo.ledgerapi.transfer.e2e.TransferFixture.newKey;
import static org.assertj.core.api.Assertions.assertThat;

class TransferReplayE2ETest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate http;

    private TransferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new TransferFixture(http);
    }

    @Test
    void shouldReplayOriginalResponseWhenSameRequestIsSentAgain() {
        String key = newKey();
        ResponseEntity<String> original = fixture.transferFromAliceToBob(key, "100.00");

        ResponseEntity<String> replay = fixture.transferFromAliceToBob(key, "100.00");

        assertCreated(replay);
        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertThat(replay.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void shouldMoveMoneyOnlyOnceWhenSameRequestIsSentManyTimes() {
        String key = newKey();

        fixture.transferFromAliceToBob(key, "100.00");
        fixture.transferFromAliceToBob(key, "100.00");
        fixture.transferFromAliceToBob(key, "100.00");

        assertBalances(fixture, "400.00", "100.00");
        assertThat(fixture.api.transactions(fixture.bob, fixture.bobAccount)).hasSize(1);
    }

    @Test
    void shouldReplayOriginalBalancesAfterBalancesHaveChanged() {
        String key = newKey();
        ResponseEntity<String> original = fixture.transferFromAliceToBob(key, "100.00");
        fixture.transferFromAliceToBob(null, "50.00");

        ResponseEntity<String> replay = fixture.transferFromAliceToBob(key, "100.00");

        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertThat(json(replay).get("fromBalanceAfter").decimalValue()).isEqualByComparingTo("400.00");
        assertBalances(fixture, "350.00", "150.00");
    }

    @Test
    void shouldReplaySuccessWhenRepeatingWouldNowFailForInsufficientFunds() {
        String key = newKey();
        ResponseEntity<String> original = fixture.transferFromAliceToBob(key, "500.00");

        ResponseEntity<String> replay = fixture.transferFromAliceToBob(key, "500.00");

        assertCreated(replay);
        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertBalances(fixture, "0.00", "500.00");
    }

    @Test
    void shouldReplayWhenSameAmountIsWrittenWithDifferentScale() {
        String key = newKey();
        ResponseEntity<String> original = fixture.transferFromAliceToBob(key, "100");

        ResponseEntity<String> replay = fixture.transferFromAliceToBob(key, "100.00");

        assertCreated(replay);
        assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertBalances(fixture, "400.00", "100.00");
    }
}
