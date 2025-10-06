package com.ecommerce.order.model;

import org.springframework.data.domain.Persistable;

/**
 * Persistable variant that allows marking aggregates as persisted after conversion/save operations.
 */
public interface StatefulPersistable<ID> extends Persistable<ID> {

    void markPersisted();
}
