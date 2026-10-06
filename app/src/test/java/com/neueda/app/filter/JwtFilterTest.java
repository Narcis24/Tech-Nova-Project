package com.neueda.app.filter;

import com.neueda.app.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtFilterTest {

    private JwtUtil jwtUtil;
    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        filter = new JwtFilter(jwtUtil, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String path, String authHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api" + path);
        request.setContextPath("/api");
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void docsArePublic() throws Exception {
        assertEquals(200, run("/swagger-ui/index.html", null).getStatus());
        assertEquals(200, run("/swagger-ui.html", null).getStatus());
        assertEquals(200, run("/v3/api-docs", null).getStatus());
    }

    @Test
    void pathsThatOnlyContainPublicWordsNeedAToken() throws Exception {
        assertEquals(401, run("/v1/accounts/author", null).getStatus());
        assertEquals(401, run("/v1/accounts/swagger", null).getStatus());
    }

    @Test
    void validTokenPasses() throws Exception {
        when(jwtUtil.isTokenValid("good")).thenReturn(true);
        when(jwtUtil.extractUsername("good")).thenReturn("someuser");

        assertEquals(200, run("/v1/accounts/ACC1", "Bearer good").getStatus());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);

        MockHttpServletResponse response = run("/v1/accounts/ACC1", "Bearer bad");

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"UNAUTHORIZED\""));
    }
}
