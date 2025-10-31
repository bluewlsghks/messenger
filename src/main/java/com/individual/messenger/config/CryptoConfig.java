package com.individual.messenger.config;

import com.individual.messenger.crypto.Aes256;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {
    // ?댁쁺?먯꽌???섍꼍蹂???몃??ㅼ젙?먯꽌 二쇱엯
    @Bean
    public Aes256 aes256() {
        String key32 = "0123456789ABCDEF0123456789ABCDEF"; // ?덉떆: 32??
        String iv16  = "ABCDEF0123456789";                   // ?덉떆: 16??
        return new Aes256(key32, iv16);
    }
}


