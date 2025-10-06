package com.ecommerce.order.config;

import com.ecommerce.order.model.Auditable;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuditingCallback implements BeforeConvertCallback<Object> {

    @Override
    public Object onBeforeConvert(Object aggregate) {
        if (aggregate instanceof Auditable auditable) {
            Instant now = Instant.now();
            if (auditable.getCreatedAt() == null) {
                auditable.setCreatedAt(now);
            }
            auditable.setUpdatedAt(now);
        }
        return aggregate;
    }
}
