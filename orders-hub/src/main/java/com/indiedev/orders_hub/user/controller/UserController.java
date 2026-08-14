package com.indiedev.orders_hub.user.controller;

import com.indiedev.orders_hub.common.response.ApiSuccessResponse;
import com.indiedev.orders_hub.exception.AuthenticatedUserMissingException;
import com.indiedev.orders_hub.user.response.UserDetailsResponse;
import com.indiedev.orders_hub.user.service.UserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserDetailsService userDetailsService;

    @GetMapping("/me")
    public ApiSuccessResponse<UserDetailsResponse> getUserInfo(@AuthenticationPrincipal Jwt jwt) {
        UserDetailsResponse userDetailsResponse = userDetailsService.getUserDetails(userId(jwt));
        return new ApiSuccessResponse<UserDetailsResponse>("User info fetched successfully", userDetailsResponse);
    }

    private long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number userId) {
            return userId.longValue();
        }
        throw new AuthenticatedUserMissingException();
    }
}
