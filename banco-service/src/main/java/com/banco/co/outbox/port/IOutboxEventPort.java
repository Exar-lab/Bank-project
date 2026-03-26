package com.banco.co.outbox.port;

import com.banco.co.outbox.model.OutboxEvent;

public interface IOutboxEventPort {
    OutboxEvent save(OutboxEvent event);
}
