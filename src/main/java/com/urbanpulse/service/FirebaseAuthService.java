package com.urbanpulse.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.dto.FirebaseTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FirebaseAuthService {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthService.class);

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Verifies the given Firebase ID token using the Firebase Admin SDK.
     *
     * @param idToken the raw Firebase ID token
     * @return FirebaseToken decoded and verified token
     * @throws FirebaseAuthException if token verification fails or token is invalid/expired
     * @throws IllegalArgumentException if token is null or empty
     */
    public FirebaseToken verifyIdToken(String idToken) throws FirebaseAuthException {
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Firebase ID token cannot be null or empty");
        }
        return firebaseAuth.verifyIdToken(idToken.trim());
    }

    /**
     * Verifies the Firebase ID token and extracts safe identity attributes.
     *
     * @param idToken the raw Firebase ID token
     * @return FirebaseTokenResponse containing authentication status, UID, and phone number
     * @throws FirebaseAuthException if token verification fails
     */
    public FirebaseTokenResponse verifyAndExtract(String idToken) throws FirebaseAuthException {
        FirebaseToken decodedToken = verifyIdToken(idToken);

        String uid = decodedToken.getUid();
        Map<String, Object> claims = decodedToken.getClaims();
        String phoneNumber = null;

        if (claims != null && claims.containsKey("phone_number")) {
            Object phoneObj = claims.get("phone_number");
            if (phoneObj != null) {
                phoneNumber = phoneObj.toString();
            }
        }

        logger.info("Firebase ID token verified successfully for UID: {}", uid);

        return FirebaseTokenResponse.builder()
                .authenticated(true)
                .firebaseUid(uid)
                .phoneNumber(phoneNumber)
                .build();
    }

    public FirebaseAuth getFirebaseAuth() {
        return firebaseAuth;
    }
}
