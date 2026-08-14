package com.indiedev.orders_hub.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

class BusinessExceptionHierarchyTest {

    @Test
    void projectExceptionsExtendBusinessExceptionWithTheirHttpStatuses() {
        BusinessException conflict = assertInstanceOf(
                BusinessException.class,
                new ConnectedAccountConflictException()
        );
        BusinessException reconnect = assertInstanceOf(
                BusinessException.class,
                new GmailConnectionRequiredException("Reconnect Gmail")
        );
        BusinessException google = assertInstanceOf(
                BusinessException.class,
                new GoogleApiException("Google failed")
        );

        assertEquals(CONFLICT, conflict.getHttpErrorStatus());
        assertEquals(CONFLICT, reconnect.getHttpErrorStatus());
        assertEquals(BAD_GATEWAY, google.getHttpErrorStatus());
    }
}
