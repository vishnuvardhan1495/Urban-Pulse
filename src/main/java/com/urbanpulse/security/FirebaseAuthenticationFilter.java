package com.urbanpulse.security;

import com.google.firebase.auth.FirebaseToken;
import com.urbanpulse.entity.User;
import com.urbanpulse.repository.UserRepository;
import com.urbanpulse.service.FirebaseAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseAuthService firebaseAuthService;
    private final UserRepository userRepository;

    public FirebaseAuthenticationFilter(FirebaseAuthService firebaseAuthService, UserRepository userRepository) {
        this.firebaseAuthService = firebaseAuthService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authHeader.substring(7).trim();
        if (idToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            FirebaseToken decodedToken = firebaseAuthService.verifyIdToken(idToken);
            Map<String, Object> claims = decodedToken.getClaims();

            String phoneNumber = null;
            if (claims != null && claims.containsKey("phone_number")) {
                Object phoneObj = claims.get("phone_number");
                if (phoneObj != null) {
                    phoneNumber = phoneObj.toString().trim();
                }
            }

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                Optional<User> userOpt = userRepository.findByMobileNumber(phoneNumber);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    String roleName = user.getRole().name();
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getMobileNumber(),
                            idToken,
                            authorities
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    logger.debug("Successfully set SecurityContext for user {} with authority ROLE_{}", user.getMobileNumber(), roleName);
                } else {
                    logger.warn("Firebase token verified for phone {} but user does not exist in PostgreSQL. Access rejected.", phoneNumber);
                }
            } else {
                logger.warn("Firebase token verified for UID {} but lacks phone_number claim.", decodedToken.getUid());
            }
        } catch (Exception ex) {
            logger.warn("Firebase authentication token verification failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
