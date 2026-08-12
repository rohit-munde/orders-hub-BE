package com.indiedev.orders_hub.connectedaccount.controller;

import com.indiedev.orders_hub.connectedaccount.service.ConnectedAccountService;
import com.indiedev.orders_hub.exception.AuthenticatedUserMissingException;
import com.indiedev.orders_hub.connectedaccount.request.GoogleConnectedAccountRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.indiedev.orders_hub.common.response.ApiSuccessResponse;
import com.indiedev.orders_hub.connectedaccount.response.ConnectedAccountResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/v1/connected-accounts")
@RequiredArgsConstructor
public class ConnectedAccountController {

    private final ConnectedAccountService connectedAccountService;

    @PostMapping("/google")
    public ApiSuccessResponse<ConnectedAccountResponse> connectGoogle(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoogleConnectedAccountRequest request
    ) {
        ConnectedAccountResponse response = connectedAccountService.connectGoogle(
                userId(jwt),
                request.serverAuthCode()
        );

        return new ApiSuccessResponse<>("Google account connected successfully", response);
    }

    private long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number userId) {
            return userId.longValue();
        }
        throw new AuthenticatedUserMissingException();
    }

    @GetMapping
    public ApiSuccessResponse<List<ConnectedAccountResponse>> getConnectedAccounts(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new ApiSuccessResponse<>(
                "Connected accounts fetched successfully",
                connectedAccountService.getConnectedAccounts(userId(jwt))
        );
    }
}
