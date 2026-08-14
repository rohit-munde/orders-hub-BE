package com.indiedev.orders_hub.connectedaccount.controller;

import com.indiedev.orders_hub.GlobalExceptionHandling;
import com.indiedev.orders_hub.config.SecurityConfig;
import com.indiedev.orders_hub.connectedaccount.response.DisconnectAccountResponse;
import com.indiedev.orders_hub.connectedaccount.service.ConnectedAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ConnectedAccountController.class,
        properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
)
@Import({SecurityConfig.class, GlobalExceptionHandling.class})
class ConnectedAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectedAccountService connectedAccountService;

    @Test
    void disconnectsGoogleAccountForTheAuthenticatedUser() throws Exception {
        when(connectedAccountService.disconnectGoogle(7, 9))
                .thenReturn(new DisconnectAccountResponse(
                        true,
                        3,
                        5,
                        "shopper@gmail.com"
                ));

        mockMvc.perform(delete("/api/v1/connected-accounts/google/9")
                        .with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Google account disconnected successfully"))
                .andExpect(jsonPath("$.payload.connectedAccountDeleted").value(true))
                .andExpect(jsonPath("$.payload.ordersDeleted").value(3))
                .andExpect(jsonPath("$.payload.emailSourcesDeleted").value(5))
                .andExpect(jsonPath("$.payload.disconnectedEmailId").value("shopper@gmail.com"));

        verify(connectedAccountService).disconnectGoogle(7, 9);
    }
}
