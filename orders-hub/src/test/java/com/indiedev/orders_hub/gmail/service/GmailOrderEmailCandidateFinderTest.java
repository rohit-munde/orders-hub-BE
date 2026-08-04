package com.indiedev.orders_hub.gmail.service;

import com.indiedev.orders_hub.gmail.client.GmailApiClient;
import com.indiedev.orders_hub.gmail.config.GmailSearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class GmailOrderEmailCandidateFinderTest {

    @Test
    void buildsConfiguredQueryAndReturnsCandidateBatch() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order", "receipt"));
        properties.setSenderDomains(List.of("amazon.in"));
        GmailOrderEmailCandidateFinder finder = new GmailOrderEmailCandidateFinder(client, properties);
        String expectedQuery = "newer_than:45d {subject:order subject:receipt from:amazon.in}";
        when(client.findMessageIds("access-token", expectedQuery, 50))
                .thenReturn(List.of("message-1", "message-2"));

        OrderEmailCandidateFinder.CandidateBatch batch = finder.find("access-token");

        assertEquals(expectedQuery, batch.query());
        assertEquals(List.of("message-1", "message-2"), batch.gmailMessageIds());
    }

    @Test
    void capsConfiguredBatchAtFiftyMessages() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setBatchSize(100);
        when(client.findMessageIds(anyString(), anyString(), eq(50))).thenReturn(List.of());

        new GmailOrderEmailCandidateFinder(client, properties).find("access-token");

        verify(client).findMessageIds(eq("access-token"), anyString(), eq(50));
    }

    @Test
    void rejectsUnsafeFilterConfigurationBeforeCallingGmail() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setSubjectKeywords(List.of("order}"));
        properties.setSenderDomains(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailOrderEmailCandidateFinder(client, properties).find("access-token")
        );
        verifyNoInteractions(client);
    }

    @Test
    void acceptsTheMaximumFortyFiveDayLookback() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties properties = new GmailSearchProperties();
        properties.setLookbackDays(45);
        when(client.findMessageIds(anyString(), anyString(), anyInt())).thenReturn(List.of());

        new GmailOrderEmailCandidateFinder(client, properties).find("access-token");

        verify(client).findMessageIds(
                "access-token",
                "newer_than:45d {subject:order subject:ordered subject:shipped subject:delivered "
                        + "subject:dispatched subject:invoice subject:receipt from:amazon.in "
                        + "from:flipkart.com from:myntra.com from:meesho.com}",
                50
        );
    }

    @Test
    void rejectsLookbacksOutsideOneToFortyFiveDays() {
        GmailApiClient client = mock(GmailApiClient.class);
        GmailSearchProperties tooShort = new GmailSearchProperties();
        tooShort.setLookbackDays(0);
        GmailSearchProperties tooLong = new GmailSearchProperties();
        tooLong.setLookbackDays(46);

        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailOrderEmailCandidateFinder(client, tooShort).find("access-token")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GmailOrderEmailCandidateFinder(client, tooLong).find("access-token")
        );
        verifyNoInteractions(client);
    }
}
