package com.individual.messenger.service;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PushValidationTest {
    @Test void acceptsKnownBrowserProvidersOnly() {
        for(String uri:List.of("https://fcm.googleapis.com/fcm/send/test", "https://updates.push.services.mozilla.com/wpush/v2/test",
                "https://web.push.apple.com/Q/test","https://example.notify.windows.com/w/?token=test"))
            assertDoesNotThrow(()->WebPushService.validateEndpoint(uri));
    }
    @Test void rejectsPrivateOriginsRedirectTargetsAndLookalikeDomains() {
        for(String uri:List.of("http://fcm.googleapis.com/test","https://127.0.0.1/test","https://169.254.169.254/",
                "https://fcm.googleapis.com.evil.invalid/test","https://fcm.googleapis.com@evil.invalid/",
                "https://user:pass@fcm.googleapis.com/test","https://fcm.googleapis.com:444/test","https://fcm.googleapis.com/test#fragment"))
            assertThrows(IllegalArgumentException.class,()->WebPushService.validateEndpoint(uri));
    }
    @Test void acceptsARealCurvePointAndRejectsInvalidPoints() {
        byte[] point=HexFormat.of().parseHex("04"+"6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"+
                "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");
        String auth=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        assertDoesNotThrow(()->WebPushService.validateKeys(Base64.getUrlEncoder().encodeToString(point),auth));
        point[10]^=1;
        assertThrows(IllegalArgumentException.class,()->WebPushService.validateKeys(Base64.getUrlEncoder().encodeToString(point),auth));
        assertThrows(IllegalArgumentException.class,()->WebPushService.validateKeys("invalid","invalid"));
    }
    @Test void perSubscriptionMuteIsCheckedWithoutDisclosingOtherSubscriptions() {
        assertFalse(WebPushService.muted(new Document(),"room"));
        assertTrue(WebPushService.muted(new Document("mutedRooms",List.of("room")),"room"));
        assertFalse(WebPushService.muted(new Document("mutedRooms",List.of("other")),"room"));
    }
}
