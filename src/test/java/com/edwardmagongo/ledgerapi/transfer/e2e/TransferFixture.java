package com.edwardmagongo.ledgerapi.transfer.e2e;

import com.edwardmagongo.ledgerapi.support.LedgerApi;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/** Fresh starting state for one test: Alice has 500.00 GBP and Bob has an empty GBP account. */
final class TransferFixture {

    final LedgerApi api;
    final String alice;
    final String bob;
    final String aliceAccount;
    final String bobAccount;

    TransferFixture(TestRestTemplate http) {
        api = new LedgerApi(http);
        alice = api.registerAndLogin();
        bob = api.registerAndLogin();
        aliceAccount = api.openAccount(alice, "GBP");
        bobAccount = api.openAccount(bob, "GBP");
        api.deposit(alice, aliceAccount, "500.00");
    }

    ResponseEntity<String> transferFromAliceToBob(String key, String amount) {
        return api.transfer(alice, key, aliceAccount, bobAccount, amount);
    }

    static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}
