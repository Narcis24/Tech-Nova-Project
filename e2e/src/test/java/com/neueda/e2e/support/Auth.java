package com.neueda.e2e.support;

import static io.restassured.RestAssured.given;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/** Raw calls to auth-service, for tests of its own API; {@link Trader} covers the happy path. */
public final class Auth {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Auth() {
    }

    /** A username nobody has registered: "e2e_" and 8 hex digits, within the 8-16 letters, digits and underscore. */
    public static String newUsername() {
        return "e2e_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Map<String, String> credentials(String username) {
        return Map.of("username", username, "password", Trader.PASSWORD);
    }

    /** POST /v1/auth/{path} with a JSON body, accepted or not. */
    public static Response post(String path, Object body) {
        return given().baseUri(Stack.authUrl()).contentType(ContentType.JSON).body(body).post("/v1/auth/" + path);
    }

    /** GET a path under /auth, e.g. /health. */
    public static Response get(String path) {
        return given().baseUri(Stack.authUrl()).get(path);
    }

    /** Registers the user and logs in; returns the token response. */
    public static JsonPath registerAndLogin(String username) {
        post("register", credentials(username)).then().statusCode(201);
        return post("login", credentials(username)).then().statusCode(200).extract().jsonPath();
    }

    public static Response refresh(String refreshToken) {
        return post("refresh", Map.of("refreshToken", refreshToken));
    }

    public static Response logout(String refreshToken) {
        return post("logout", Map.of("refreshToken", refreshToken));
    }

    /** Test setup only: lets the user's refresh tokens run out, which the API cannot do before 7 days. */
    public static void expireRefreshTokens(String username) throws SQLException {
        try (Connection db = Stack.openDatabase();
             PreparedStatement expire = db.prepareStatement(
                 "UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE username = ?")) {
            expire.setString(1, username);
            if (expire.executeUpdate() == 0) {
                throw new IllegalStateException(username + " has no refresh tokens");
            }
        }
    }

    /** The JSON of one part of a JWT, 0 for the header and 1 for the claims; the signature is not checked. */
    public static Map<String, Object> jwtPart(String token, int part) {
        try {
            return JSON.readValue(Base64.getUrlDecoder().decode(token.split("\\.")[part]), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
