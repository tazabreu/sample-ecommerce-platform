package com.ecommerce.customer.service;

import com.ecommerce.customer.model.OrderNumberSequence;
import com.ecommerce.customer.repository.OrderNumberSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderNumberService.
 * Tests order number generation logic without Spring context.
 */
@ExtendWith(MockitoExtension.class)
class OrderNumberServiceTest {

    @Mock
    private OrderNumberSequenceRepository sequenceRepository;

    @InjectMocks
    private OrderNumberService orderNumberService;

    private String todayKey;

    @BeforeEach
    void setUp() {
        todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Test
    void generateOrderNumber_shouldGenerateFirstOrderOfDay() {
        // Given
        when(sequenceRepository.findByDateKeyWithLock(todayKey)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(OrderNumberSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String orderNumber = orderNumberService.generateOrderNumber();

        // Then
        assertThat(orderNumber).isEqualTo("ORD-" + todayKey + "-1");
        verify(sequenceRepository).save(argThat(seq ->
            seq.getDateKey().equals(todayKey) && seq.getLastSequence() == 1
        ));
    }

    @Test
    void generateOrderNumber_shouldGenerateSequentialOrders() {
        // Given
        OrderNumberSequence sequence = new OrderNumberSequence(todayKey, 0);
        when(sequenceRepository.findByDateKeyWithLock(todayKey)).thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(OrderNumberSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String order1 = orderNumberService.generateOrderNumber();
        sequence.setLastSequence(1);
        String order2 = orderNumberService.generateOrderNumber();
        sequence.setLastSequence(2);
        String order3 = orderNumberService.generateOrderNumber();

        // Then
        assertThat(order1).isEqualTo("ORD-" + todayKey + "-1");
        assertThat(order2).isEqualTo("ORD-" + todayKey + "-2");
        assertThat(order3).isEqualTo("ORD-" + todayKey + "-3");
    }

    @Test
    void generateOrderNumber_shouldSupportLargeSequenceNumbers() {
        // Given - simulate 1000th order of the day
        OrderNumberSequence sequence = new OrderNumberSequence(todayKey, 999);
        when(sequenceRepository.findByDateKeyWithLock(todayKey)).thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(OrderNumberSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String orderNumber = orderNumberService.generateOrderNumber();

        // Then
        assertThat(orderNumber).isEqualTo("ORD-" + todayKey + "-1000");
        verify(sequenceRepository).save(argThat(seq -> seq.getLastSequence() == 1000));
    }

    @Test
    void generateOrderNumber_shouldSupportVeryLargeSequenceNumbers() {
        // Given - simulate 100,000th order of the day
        OrderNumberSequence sequence = new OrderNumberSequence(todayKey, 99999);
        when(sequenceRepository.findByDateKeyWithLock(todayKey)).thenReturn(Optional.of(sequence));
        when(sequenceRepository.save(any(OrderNumberSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String orderNumber = orderNumberService.generateOrderNumber();

        // Then
        assertThat(orderNumber).isEqualTo("ORD-" + todayKey + "-100000");
        verify(sequenceRepository).save(argThat(seq -> seq.getLastSequence() == 100000));
    }

    @Test
    void generateOrderNumber_shouldResetSequenceForNewDay() {
        // Given - new day with no existing sequence
        when(sequenceRepository.findByDateKeyWithLock(todayKey)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(OrderNumberSequence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String orderNumber = orderNumberService.generateOrderNumber();

        // Then - should start at 1 for new day
        assertThat(orderNumber).isEqualTo("ORD-" + todayKey + "-1");
    }

}
