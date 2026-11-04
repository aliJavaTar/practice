package com.edwardmagongo.ledgerapi.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A thin client over the real HTTP API, used by end-to-end tests to arrange state and send
 * requests exactly as an external caller would. No repositories, no services, no mocks.
 *
 * <p>Arrangement helpers ({@link #registerAndLogin}, {@link #openAccount}, ...) assert their own
 * success, so a broken fixture fails loudly at the step that broke rather than as a confusing
 * assertion later in the test.
 */
public final class LedgerApi {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    // BigDecimal for every JSON number: money must never pass through double.
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private final TestRestTemplate http;

    public LedgerApi(TestRestTemplate http) {
        this.http = http;
    }

    // ---- arrangement -------------------------------------------------------------------------

    /** Registers a brand-new user with a unique email and returns their JWT. */
    public String registerAndLogin() {
        String credentials = """
                {"email":"user-%s@example.com","password":"s3cretpassword"}"""
                .formatted(UUID.randomUUID());
        expect(send(HttpMethod.POST, "/api/auth/register", null, null, credentials), HttpStatus.CREATED);
        ResponseEntity<String> login =
                expect(send(HttpMethod.POST, "/api/auth/login", null, null, credentials), HttpStatus.OK);
        return json(login).get("token").asText();
    }

    public UUID userId(String token) {
        return UUID.fromString(json(expect(send(HttpMethod.GET, "/api/me", token, null, null), HttpStatus.OK))
                .get("id").asText());
    }

    public String openAccount(String token, String currency) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/api/accounts", token, null,
                "{\"currency\":\"" + currency + "\"}");
        return json(expect(response, HttpStatus.CREATED)).get("id").asText();
    }

    public void deposit(String token, String accountId, String amount) {
        expect(deposit(token, null, accountId, amount), HttpStatus.CREATED);
    }

    public ResponseEntity<String> deposit(String token, String key, String accountId, String amount) {
        return send(HttpMethod.POST, "/api/accounts/" + accountId + "/deposit", token, key,
                "{\"amount\":" + amount + "}");
    }

    public void closeAccount(String token, String accountId) {
        expect(send(HttpMethod.DELETE, "/api/accounts/" + accountId, token, null, null), HttpStatus.NO_CONTENT);
    }

    // ---- the system under test ---------------------------------------------------------------

    /**
     * Sends a transfer. {@code token} and {@code key} may be null to omit the header. The amount is
     * a raw JSON number literal, so tests control its exact written form ("100" vs "100.00").
     */
    public ResponseEntity<String> transfer(String token, String key, String from, String to, String amount) {
        String body = "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to
                + "\",\"amount\":" + amount + "}";
        return send(HttpMethod.POST, "/api/transfers", token, key, body);
    }

    // ---- observation -------------------------------------------------------------------------

    public BigDecimal balance(String token, String accountId) {
        return json(expect(send(HttpMethod.GET, "/api/accounts/" + accountId, token, null, null), HttpStatus.OK))
                .get("balance").decimalValue();
    }

    /** The account's ledger entries, newest first. */
    public List<JsonNode> transactions(String token, String accountId) {
        JsonNode page = json(expect(
                send(HttpMethod.GET, "/api/accounts/" + accountId + "/transactions?size=100", token, null, null),
                HttpStatus.OK));
        return StreamSupport.stream(page.get("content").spliterator(), false).toList();
    }

    public static JsonNode json(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (JsonProcessingException ex) {
            throw new AssertionError("response body is not JSON: " + response.getBody(), ex);
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private ResponseEntity<String> send(HttpMethod method, String path, String token, String key, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (key != null) {
            headers.set(IDEMPOTENCY_KEY, key);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private static ResponseEntity<String> expect(ResponseEntity<String> response, HttpStatus status) {
        assertThat(response.getStatusCode())
                .as("fixture call failed with body: %s", response.getBody())
                .isEqualTo(status);
        return response;
    }
}
