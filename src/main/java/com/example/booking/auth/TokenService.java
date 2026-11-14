package com.example.booking.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class TokenService {
        
        private final JwtEncoder jwtEncoder;
        private final JwtProperties jwtProperties;
        private final Clock clock;

        //Method untuk mengeluarkan token JWT berdasarkan informasi pengguna yang diberikan. 
        //Token ini berisi klaim seperti issuer, waktu diterbitkan, waktu kedaluwarsa, subjek (ID pengguna), email, dan peran pengguna.
        //Contoh output dari method ini memiliki format pengiriman data misalnya seperti berikut:
        //{
        //  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJYm9va2luZy1hcGkiLCJpYXQiOjE2OTM4NjQwMDAsImV4cCI6MTY5Mzg2NzAwMCwic3ViIjoiMTIzNDU2NyIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSIsInJvbGUiOlsiVVNFUiJdfQ.abc123xyz456"
        //}

        
        public String issue(User user){
            Instant now = clock.instant();
            JwtClaimsSet claims = JwtClaimsSet.builder()
             .issuer("booking-api")
             .issuedAt(now)
             .expiresAt(now.plus(jwtProperties.ttl()))
             .subject(user.getId().toString())
             .claim("email", user.getEmail())
             .claim("role", List.of(user.getRole().name()))
             .build();
            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            return jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
        }
        
        //Method untuk mendapatkan waktu hidup (TTL) dari token JWT dalam satuan detik.
        public long ttlSeconds(){
            return jwtProperties.ttl().toSeconds();
        }

}
