package com.ecommerce.customer.config;

import com.ecommerce.customer.model.StatefulPersistable;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.mapping.event.AfterSaveCallback;
import org.springframework.stereotype.Component;

@Component
public class PersistableStateCallback implements AfterConvertCallback<Object>, AfterSaveCallback<Object> {

    @Override
    public Object onAfterConvert(Object aggregate) {
        markPersisted(aggregate);
        return aggregate;
    }

    @Override
    public Object onAfterSave(Object aggregate) {
        markPersisted(aggregate);
        return aggregate;
    }

    private void markPersisted(Object aggregate) {
        if (aggregate instanceof StatefulPersistable<?> persistable) {
            persistable.markPersisted();
        }
    }
}
