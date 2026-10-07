package com.individual.messenger.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class OperatorAccessTest {
    @Test void disabledByDefaultEvenForBearerJwt() {
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer user-token");
        assertFalse(new OperatorAccess("").allows(request));
    }
    @Test void requiresSeparateStrongExactSecret() {
        assertThrows(IllegalArgumentException.class,()->new OperatorAccess("short"));
        var access=new OperatorAccess("a".repeat(32));var wrong=new MockHttpServletRequest();wrong.addHeader("X-Operations-Token","b".repeat(32));
        assertFalse(access.allows(wrong));var right=new MockHttpServletRequest();right.addHeader("X-Operations-Token","a".repeat(32));assertTrue(access.allows(right));
    }
}
