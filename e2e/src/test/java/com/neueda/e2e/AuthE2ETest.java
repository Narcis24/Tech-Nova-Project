package com.neueda.e2e;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.e2e.support.Stack;

import io.restassured.http.ContentType;

/** Tokens issued by auth-service are honoured by app, and nothing else is. */
class AuthE2ETest extends E2ETestBase {

    @Test
    @DisplayName("A token from auth-service is accepted by app")
    void tokenFromAuthIsAccepted() {
        me.request().get("/v1/accounts").then().statusCode(200);
    }

    @Test
    @DisplayName("App refuses a request with no token")
    void missingTokenIsRefused() {
        given().baseUri(Stack.appUrl()).get("/v1/accounts").then().statusCode(401);
    }

    @Test
    @DisplayName("App refuses a token whose signature was tampered with")
    void tamperedTokenIsRefused() {
        String tampered = me.token().substring(0, me.token().length() - 4) + "AAAA";

        given().baseUri(Stack.appUrl()).auth().oauth2(tampered).get("/v1/accounts").then().statusCode(401);
    }

    @Test
    @DisplayName("Auth refuses a wrong password with 401")
    void wrongPasswordIsRefused() {
        given().baseUri(Stack.authUrl()).contentType(ContentType.JSON)
            .body(Map.of("username", me.username(), "password", "Wrong-Password1"))
            .post("/v1/auth/login").then().statusCode(401);
    }
}
