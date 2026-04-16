package com.banco.co.notification.email.adapter;

import com.banco.co.notification.email.dto.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpEmailDispatcherTest {

    @Test
    void testDispatch_WhenNoOpDispatcher_ThenDoesNotThrow() {
        NoOpEmailDispatcher dispatcher = new NoOpEmailDispatcher();
        EmailMessage message = new EmailMessage("evt-1", "test@banco.co", "subject", "<p>body</p>");

        assertThatCode(() -> dispatcher.dispatch(message)).doesNotThrowAnyException();
    }
}
