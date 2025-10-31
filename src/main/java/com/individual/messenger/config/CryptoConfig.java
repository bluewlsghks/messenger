package com.individual.messenger.config;

import com.individual.messenger.crypto.Aes256;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {
    // ?´ì˜?ì„œ???˜ê²½ë³€???¸ë??¤ì •?ì„œ ì£¼ì…
    @Bean
    public Aes256 aes256() {
        String key32 = "0123456789ABCDEF0123456789ABCDEF"; // ?ˆì‹œ: 32??
        String iv16  = "ABCDEF0123456789";                   // ?ˆì‹œ: 16??
        return new Aes256(key32, iv16);
    }
}

