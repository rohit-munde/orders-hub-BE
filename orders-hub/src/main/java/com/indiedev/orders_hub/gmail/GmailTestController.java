package com.indiedev.orders_hub.gmail;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/gmail")
public class GmailTestController {

    private final RestClient restClient = RestClient.create();

    @GetMapping("/messages")
    public Object getMessages(
            @RegisteredOAuth2AuthorizedClient("google")
            OAuth2AuthorizedClient client) {

        String accessToken = client.getAccessToken().getTokenValue();

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("gmail.googleapis.com")
                        .path("/gmail/v1/users/me/messages")
                        .queryParam("maxResults", 20)
                        .queryParam(
                                "q",
                                "newer_than:1y (\"order confirmed\" OR \"your order\" OR shipped OR delivered)"
                        )
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Object.class);
    }

    @GetMapping("/messages/{id}")
    public Object getMessage(
            @PathVariable String id,
            @RegisteredOAuth2AuthorizedClient("google")
            OAuth2AuthorizedClient client) {

        String accessToken = client.getAccessToken().getTokenValue();

        return restClient.get()
                .uri(
                        "https://gmail.googleapis.com/gmail/v1/users/me/messages/{id}?format=full",
                        id
                )
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Object.class);
    }
}
