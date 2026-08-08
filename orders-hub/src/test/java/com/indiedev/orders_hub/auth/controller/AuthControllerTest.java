package com.indiedev.orders_hub.auth.controller;

import com.indiedev.orders_hub.GlobalExceptionHandling;
import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.auth.service.AuthService;
import com.indiedev.orders_hub.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
)
@Import({SecurityConfig.class, GlobalExceptionHandling.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void wrapsSuccessfulGoogleAuthenticationInTheSharedApiEnvelope() throws Exception {
        when(authService.loginWithGoogle("google-id-token", "server-auth-code"))
                .thenReturn(new AuthResponse(
                        "signed-jwt",
                        new AuthResponse.UserInfo(42, "Test User", "user@example.com", null),
                        new AuthResponse.ConnectedAccountInfo(7, "user@example.com", "CONNECTED"),
                        null
                ));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType("application/json")
                        .content("""
                                {
                                  "idToken": "google-id-token",
                                  "serverAuthCode": "server-auth-code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Authenticated successfully"))
                .andExpect(jsonPath("$.payload.appToken").value("signed-jwt"))
                .andExpect(jsonPath("$.payload.user.id").value(42))
                .andExpect(jsonPath("$.payload.connectedAccount.id").value(7));

        verify(authService).loginWithGoogle("google-id-token", "server-auth-code");
    }
}
