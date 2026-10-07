package com.neueda.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Auth;
import com.neueda.e2e.support.Stack;

import io.restassured.path.json.JsonPath;

/** auth-service's API as contracts/auth-api.yml describes it, against the real database. */
class AuthApiE2ETest extends E2ETestBase {

    @Test
    @DisplayName("Register returns 201 with the username; the same name again is 409")
    void registerThenConflict() {
        String username = Auth.newUsername();

        Auth.post("register", Auth.credentials(username)).then().statusCode(201)
            .body("size()", equalTo(1))
            .body("username", equalTo(username));
        Auth.post("register", Auth.credentials(username)).then().statusCode(409)
            .body("statusCode", equalTo(409))
            .body("error", equalTo("Conflict"))
            .body("message", equalTo("Username already registered"));
    }

    @Test
    @DisplayName("Login returns a Bearer access token for 900 seconds and a refresh token")
    void loginReturnsTokens() {
        JsonPath tokens = Auth.registerAndLogin(Auth.newUsername());

        assertEquals(Set.of("accessToken", "refreshToken", "tokenType", "expiresIn"),
            tokens.getMap("$").keySet());
        assertEquals("Bearer", tokens.getString("tokenType"));
        assertEquals(900, tokens.getInt("expiresIn"));
        assertNotEquals("", tokens.getString("refreshToken"));
    }

    @Test
    @DisplayName("The access token is HS256 with exactly sub, roles, iat and exp, valid for 15 minutes")
    void accessTokenClaims() {
        String username = Auth.newUsername();
        String accessToken = Auth.registerAndLogin(username).getString("accessToken");

        assertEquals(Map.of("alg", "HS256", "typ", "JWT"), Auth.jwtPart(accessToken, 0));
        Map<String, Object> claims = Auth.jwtPart(accessToken, 1);
        assertEquals(Set.of("sub", "roles", "iat", "exp"), claims.keySet());
        assertEquals(username, claims.get("sub"));
        assertEquals(List.of("TRADER"), claims.get("roles"));
        assertEquals(900, ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue());
    }

    @Test
    @DisplayName("A wrong password and an unknown username get the same 401 body")
    void failedLoginsLookTheSame() {
        String username = Auth.newUsername();
        Auth.post("register", Auth.credentials(username)).then().statusCode(201);
        Map<String, String> wrongPassword = Map.of("username", username, "password", "Wrong-Password1");
        Map<String, String> unknownUser = Map.of("username", Auth.newUsername(), "password", "Wrong-Password1");

        String wrong = Auth.post("login", wrongPassword).then().statusCode(401).extract().asString();
        String unknown = Auth.post("login", unknownUser).then().statusCode(401).extract().asString();

        assertEquals(wrong, unknown);
        Auth.post("login", unknownUser).then()
            .body("statusCode", equalTo(401))
            .body("error", equalTo("Unauthorized"))
            .body("message", equalTo("Invalid username or password"));
    }

    @Test
    @DisplayName("Refresh issues new tokens that app accepts, and the used refresh token stops working")
    void refreshRotates() {
        String used = Auth.registerAndLogin(Auth.newUsername()).getString("refreshToken");

        JsonPath next = Auth.refresh(used).then().statusCode(200)
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", equalTo(900))
            .body("refreshToken", not(equalTo(used)))
            .extract().jsonPath();

        given().baseUri(Stack.appUrl()).auth().oauth2(next.getString("accessToken"))
            .get("/v1/accounts").then().statusCode(200);
        Auth.refresh(used).then().statusCode(401)
            .body("statusCode", equalTo(401))
            .body("error", equalTo("Unauthorized"))
            .body("message", equalTo("Invalid or expired refresh token"));
        Auth.refresh(next.getString("refreshToken")).then().statusCode(200);
    }

    @Test
    @DisplayName("An unknown or expired refresh token is refused with 401")
    void unusableRefreshTokenIsRefused() throws SQLException {
        String username = Auth.newUsername();
        String refreshToken = Auth.registerAndLogin(username).getString("refreshToken");

        Auth.refresh("not-a-real-token").then().statusCode(401);
        Auth.expireRefreshTokens(username);
        Auth.refresh(refreshToken).then().statusCode(401);
    }

    @Test
    @DisplayName("Logout revokes the refresh token with 204, and is idempotent")
    void logoutRevokes() {
        String refreshToken = Auth.registerAndLogin(Auth.newUsername()).getString("refreshToken");

        Auth.logout(refreshToken).then().statusCode(204);
        Auth.refresh(refreshToken).then().statusCode(401);
        Auth.logout(refreshToken).then().statusCode(204);
        Auth.logout("not-a-real-token").then().statusCode(204);
    }

    @Test
    @DisplayName("A broken field rule is refused with 400 and one message per rule")
    void invalidCredentialsAreRefused() {
        Auth.post("register", Map.of("username", "ab", "password", "short")).then().statusCode(400)
            .body("statusCode", equalTo(400))
            .body("error", equalTo("Bad Request"))
            .body("message", hasItems(
                "username must be longer than or equal to 8 characters",
                "password must be longer than or equal to 12 characters"));
        Auth.post("login", Map.of("username", "has-dash1", "password", "Something123@")).then().statusCode(400)
            .body("message", hasItem("username must match /^[A-Za-z0-9_]+$/ regular expression"));
    }

    @Test
    @DisplayName("Unknown fields and a missing refresh token are refused with 400")
    void unexpectedBodiesAreRefused() {
        Map<String, String> withRole = new HashMap<>(Auth.credentials(Auth.newUsername()));
        withRole.put("role", "ADMIN");

        Auth.post("register", withRole).then().statusCode(400)
            .body("message", hasItem("property role should not exist"));
        Auth.post("refresh", Map.of()).then().statusCode(400);
        Auth.post("logout", Map.of()).then().statusCode(400);
    }

    @Test
    @DisplayName("Health, Swagger UI and the OpenAPI document are served without a token")
    void operationalEndpoints() {
        Auth.get("/health").then().statusCode(200);
        Auth.get("/docs").then().statusCode(200);
        assertEquals(Set.of("/v1/auth/register", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/logout"),
            Auth.get("/docs-json").then().statusCode(200).extract().jsonPath().getMap("paths").keySet());
    }
}
