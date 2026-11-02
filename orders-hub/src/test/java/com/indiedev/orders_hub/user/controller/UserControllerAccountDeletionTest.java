package com.indiedev.orders_hub.user.controller;

import com.indiedev.orders_hub.GlobalExceptionHandling;
import com.indiedev.orders_hub.config.SecurityConfig;
import com.indiedev.orders_hub.user.response.AccountDeletionResponse;
import com.indiedev.orders_hub.user.service.AccountDeletionService;
import com.indiedev.orders_hub.user.service.UserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
)
@Import({SecurityConfig.class, GlobalExceptionHandling.class})
class UserControllerAccountDeletionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @Test
    void deletesTheAuthenticatedUsersAccount() throws Exception {
        when(accountDeletionService.deleteAccount(7)).thenReturn(new AccountDeletionResponse(true, 2, 14));

        mockMvc.perform(delete("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.claim("userId", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Account deleted successfully"))
                .andExpect(jsonPath("$.payload.accountDeleted").value(true))
                .andExpect(jsonPath("$.payload.connectedAccountsDeleted").value(2))
                .andExpect(jsonPath("$.payload.ordersDeleted").value(14));

        verify(accountDeletionService).deleteAccount(7);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        verify(accountDeletionService, never()).deleteAccount(anyLong());
    }
}
