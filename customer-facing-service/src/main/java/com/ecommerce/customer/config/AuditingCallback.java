package com.ecommerce.customer.config;

import com.ecommerce.customer.model.Auditable;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AuditingCallback implements BeforeConvertCallback<Object> {

    @Override
    public Object onBeforeConvert(Object aggregate) {
        Instant now = Instant.now();

        if (aggregate instanceof com.ecommerce.customer.model.Cart cart) {
            cart.initializeItems();
            cart.getItems().forEach(item -> {
                if (item.getCreatedAt() == null) {
                    item.setCreatedAt(now);
                }
                item.setUpdatedAt(now);
            });
        }

        if (aggregate instanceof Auditable auditable) {
            if (auditable.getCreatedAt() == null) {
                auditable.setCreatedAt(now);
            }
            auditable.setUpdatedAt(now);
        }
        return aggregate;
    }
}
