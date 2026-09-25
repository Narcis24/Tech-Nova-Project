# JWT Authentication Filter

## Overview

The **JwtAuthenticationFilter** is a Spring Security filter that validates JWT tokens on every HTTP request. It's the gatekeeper of the application, ensuring only authenticated users with valid tokens can access protected endpoints.

## Why It's Important

### Security Benefits
1. **Stateless Authentication** - No need to store session data on the server
2. **User Enumeration Prevention** - Same error message for missing username or invalid password
3. **Token Expiration** - Tokens expire automatically, limiting exposure if compromised
4. **Signature Verification** - Ensures tokens haven't been tampered with
5. **Per-Request Validation** - Every single request is validated, can't "slip through"

### Performance Benefits
- **Fast validation** - JWT verification is < 1ms (no database calls)
- **Scalable** - Works seamlessly with microservices
- **Stateless** - No server-side session storage needed

## How It Works

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ Client sends HTTP request                                       │
│ GET /api/portfolio                                              │
│ Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: JwtAuthenticationFilter intercepts request             │
│ (runs BEFORE controller)                                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Extract token from Authorization header                │
│ "Bearer eyJhbGciOi..." → "eyJhbGciOi..."                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Validate token signature & expiration                  │
│ ✅ Signature matches secret key?                                │
│ ✅ Token hasn't expired?                                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                    ┌──────┴──────┐
                    │             │
              ✅ VALID      ❌ INVALID/EXPIRED
                    │             │
                    ▼             ▼
          ┌────────────────┐  ┌──────────────┐
          │ Extract user   │  │ Skip         │
          │ from token     │  │ authentication
          │ "john_doe"     │  │ setup
          └────────┬───────┘  └──────┬───────┘
                   │                 │
                   ▼                 ▼
          ┌────────────────┐  ┌──────────────────┐
          │ Set in         │  │ SecurityContext  │
          │ SecurityContext│  │ stays EMPTY      │
          └────────┬───────┘  └──────┬───────────┘
                   │                 │
                   └────────┬────────┘
                            ▼
          ┌──────────────────────────────────┐
          │ STEP 4: Continue to controller   │
          └────────────┬─────────────────────┘
                       │
                ┌──────┴──────┐
                │             │
            ✅ USER        ❌ NO USER
          AUTHENTICATED   AUTHENTICATED
                │             │
                ▼             ▼
          ┌─────────────┐  ┌──────────────────┐
          │ Allow       │  │ Spring Security  │
          │ request     │  │ rejects with     │
          │ Process     │  │ 401 Unauthorized │
          │             │  │                  │
          │ Controller  │  │ (hits @Secured)  │
          │ processes   │  │                  │
          └─────────────┘  └──────────────────┘
```

## Code Workflow

### Sample Valid Token Request
```bash
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     http://localhost:8080/api/portfolio
```

**Result:**
1. ✅ Filter extracts token
2. ✅ Token signature verified
3. ✅ Token not expired
4. ✅ Username "john_doe" extracted
5. ✅ Authentication set in SecurityContext
6. ✅ Controller receives request as authenticated user
7. ✅ Returns portfolio data (200 OK)

### Missing Token Request
```bash
curl http://localhost:8080/api/portfolio
```

**Result:**
1. ❌ No Authorization header
2. ❌ Token extraction returns null
3. ❌ No authentication setup
4. ❌ SecurityContext is empty
5. ❌ Spring Security's `@Secured` rules reject
6. ❌ Returns 401 Unauthorized

### Invalid/Expired Token Request
```bash
curl -H "Authorization: Bearer invalid_token_123" \
     http://localhost:8080/api/portfolio
```

**Result:**
1. ✅ Filter extracts "invalid_token_123"
2. ❌ Token signature verification fails
3. ❌ No authentication setup
4. ❌ SecurityContext is empty
5. ❌ Spring Security rejects
6. ❌ Returns 401 Unauthorized

## Implementation Details

### Location
```
auth/src/main/java/com/neueda/auth/filter/JwtAuthenticationFilter.java
```

### Key Methods

#### `doFilterInternal()`
- Runs on **every request** (exactly once per request)
- Handles token extraction and validation
- Sets authentication if token is valid
- Catches exceptions to prevent crashes

#### `extractToken()`
- Parses Authorization header
- Expects format: `Bearer <token>`
- Returns token string or null

## Integration with Spring Security

### In SecurityConfig
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/v1/auth/**").permitAll()    // Login/register open
            .anyRequest().authenticated()                   // Everything else needs auth
        )
        .addFilterBefore(
            jwtAuthenticationFilter, 
            UsernamePasswordAuthenticationFilter.class      // Add before Spring's default filter
        )
        .csrf().disable();
    
    return http.build();
}
```
