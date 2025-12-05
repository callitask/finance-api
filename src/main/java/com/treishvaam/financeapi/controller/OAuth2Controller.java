package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.User;
import com.treishvaam.financeapi.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.io.IOException;
import java.time.Instant;

@Controller
@RequestMapping("/api/v1") // Versioned
public class OAuth2Controller {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/login/oauth2/code/linkedin")
    public void handleLinkedInCallback(@RegisteredOAuth2AuthorizedClient("linkedin") OAuth2AuthorizedClient authorizedClient,
                                       @AuthenticationPrincipal OAuth2User oauth2User,
                                       HttpServletResponse response) throws IOException {
        if (oauth2User == null) {
            response.sendRedirect("/login?error=true");
            return;
        }

        String userUrn = oauth2User.getAttribute("sub");
        String userEmail = oauth2User.getAttribute("email");
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));

        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        Instant tokenExpiry = authorizedClient.getAccessToken().getExpiresAt();

        user.setLinkedinUrn(userUrn);
        user.setLinkedinAccessToken(accessToken);
        user.setLinkedinTokenExpiry(tokenExpiry);
        userRepository.save(user);

        response.sendRedirect("https://treishfin.treishvaamgroup.com/dashboard"); // Updated to live domain
    }
}