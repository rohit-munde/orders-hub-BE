package com.indiedev.orders_hub.gmail.controller;

import com.indiedev.orders_hub.gmail.dto.GmailConnectRequest;
import com.indiedev.orders_hub.gmail.dto.GmailConnectResponse;
import com.indiedev.orders_hub.gmail.service.GmailConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gmail")
@RequiredArgsConstructor
public class GmailController {

    private final GmailConnectionService gmailConnectionService;

    @PostMapping("/connect")
    public ResponseEntity<GmailConnectResponse> connect(
            Authentication authentication,
            @Valid @RequestBody GmailConnectRequest request
    ) {
        return ResponseEntity.ok(
                gmailConnectionService.connect(authentication, request.serverAuthCode())
        );
    }
}
