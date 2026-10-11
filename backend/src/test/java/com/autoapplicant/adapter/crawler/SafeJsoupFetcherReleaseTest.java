package com.autoapplicant.adapter.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import org.jsoup.Connection;
import org.junit.jupiter.api.Test;

/** Responses the fetcher does not hand back unread must not keep their connection open. */
class SafeJsoupFetcherReleaseTest {

    private static final String START = "http://93.184.216.34/jobs/1";
    private static final String LANDING = "http://93.184.216.34/landing";
    private static final int FOUND = 302;
    private static final int NOT_FOUND = 404;

    @Test
    void redirectBodiesAreClosedAndErrorBodiesBuffered() throws IOException {
        AtomicBoolean redirectBodyClosed = new AtomicBoolean();
        Connection.Response redirect = response(FOUND);
        when(redirect.header(RedirectHops.LOCATION_HEADER)).thenReturn("/landing");
        when(redirect.cookies()).thenReturn(Map.of());
        when(redirect.bodyStream()).thenReturn(closeTracking(redirectBodyClosed));
        Connection.Response missing = response(NOT_FOUND);
        when(missing.bufferUp()).thenReturn(missing);
        Connection start = connectionReturning(redirect);
        Connection landing = connectionReturning(missing);
        SafeJsoupFetcher fetcher = new SafeJsoupFetcher(
                url -> true, url -> url.equals(LANDING) ? landing : start);

        Optional<Connection.Response> result = fetcher.fetch(START, UnaryOperator.identity());

        assertThat(result).contains(missing);
        assertThat(redirectBodyClosed).isTrue();
        verify(missing).bufferUp();
    }

    private static Connection.Response response(int status) {
        Connection.Response response = mock(Connection.Response.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    private static Connection connectionReturning(Connection.Response response) throws IOException {
        Connection connection = mock(Connection.class, RETURNS_SELF);
        when(connection.execute()).thenReturn(response);
        return connection;
    }

    private static BufferedInputStream closeTracking(AtomicBoolean closed) {
        return new BufferedInputStream(new ByteArrayInputStream(new byte[0])) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }
}
