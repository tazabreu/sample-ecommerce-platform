package com.ecommerce.order.service;

import com.ecommerce.order.model.*;
import com.ecommerce.order.payment.PaymentException;
import com.ecommerce.order.payment.PaymentRequest;
import com.ecommerce.order.payment.PaymentResult;
import com.ecommerce.order.payment.PaymentService;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedEventRepository;
import com.ecommerce.shared.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderProcessingService.
 * Tests event processing logic in isolation using mocks.
 */
@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    private OrderCreatedEvent event;
    private UUID eventId;
    private String correlationId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();

        ShippingAddressEvent shippingAddress = new ShippingAddressEvent(
                "123 Main St", "Anytown", "CA", "12345", "USA");

        CustomerEvent customer = new CustomerEvent(
                "John Doe", "john.doe@example.com", "+1234567890", shippingAddress);

        OrderItemEvent item = new OrderItemEvent(
                UUID.randomUUID().toString(),
                "Test Product",
                2,
                new BigDecimal("29.99"),
                new BigDecimal("59.98"));

        event = new OrderCreatedEvent(
                eventId.toString(),
                "ORDER_CREATED",
                "1.0",
                Instant.now().toString(),
                correlationId,
                UUID.randomUUID().toString(),
                "ORD-20241201-001",
                List.of(item),
                customer,
                new BigDecimal("59.98"));
    }

    @Test
    void processOrderCreated_shouldCreateOrderSuccessfully() {
        // Given
        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(orderRepository).save(any(Order.class));
        verify(acknowledgment).acknowledge();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderNumber()).isEqualTo("ORD-20241201-001");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("john.doe@example.com");
        assertThat(savedOrder.getOrderItems()).hasSize(1);
    }

    @Test
    void processOrderCreated_shouldHandleIdempotency_whenEventAlreadyProcessed() {
        // Given
        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(true);

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processOrderCreated_shouldCreatePaymentTransaction() {
        // Given
        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getPaymentTransactions()).hasSize(1);
        PaymentTransaction transaction = savedOrder.getPaymentTransactions().get(0);
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("59.98"));
    }

    @Test
    void processOrderCreated_shouldMapOrderItemsCorrectly() {
        // Given
        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderItems()).hasSize(1);
        OrderItem orderItem = savedOrder.getOrderItems().get(0);
        assertThat(orderItem.getProductName()).isEqualTo("Test Product");
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getUnitPrice()).isEqualTo(new BigDecimal("29.99"));
        assertThat(orderItem.getTotalPrice()).isEqualTo(new BigDecimal("59.98"));
    }

    @Test
    void processOrderCreated_shouldMapCustomerInfoCorrectly() {
        // Given
        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getCustomerName()).isEqualTo("John Doe");
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("john.doe@example.com");
        assertThat(savedOrder.getCustomerPhone()).isEqualTo("+1234567890");
        assertThat(savedOrder.getShippingAddress().getStreet()).isEqualTo("123 Main St");
        assertThat(savedOrder.getShippingAddress().getCity()).isEqualTo("Anytown");
        assertThat(savedOrder.getShippingAddress().getState()).isEqualTo("CA");
        assertThat(savedOrder.getShippingAddress().getPostalCode()).isEqualTo("12345");
        assertThat(savedOrder.getShippingAddress().getCountry()).isEqualTo("USA");
    }

    @Test
    void processPaymentAsync_shouldProcessPaymentSuccessfully() throws PaymentException {
        // Given
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20241201-001");
        order.setTotalAmount(new BigDecimal("59.98"));

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("59.98"));
        order.addPaymentTransaction(transaction);

        PaymentResult paymentResult = new PaymentResult(true, "txn_123", "Payment successful");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(paymentResult);

        // When
        orderProcessingService.processPaymentAsync(order);

        // Then
        verify(paymentService).processPayment(any(PaymentRequest.class));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(transaction.getExternalTransactionId()).isEqualTo("txn_123");
    }

    @Test
    void processPaymentAsync_shouldHandlePaymentFailure() throws PaymentException {
        // Given
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20241201-001");
        order.setTotalAmount(new BigDecimal("59.98"));

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("59.98"));
        order.addPaymentTransaction(transaction);

        PaymentException paymentException = new PaymentException("Payment gateway timeout");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenThrow(paymentException);

        // When
        orderProcessingService.processPaymentAsync(order);

        // Then
        verify(paymentService).processPayment(any(PaymentRequest.class));
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transaction.getFailureReason()).isEqualTo("Payment gateway timeout");
    }

    @Test
    void processPaymentAsync_shouldUpdateOrderStatus_whenPaymentSuccessful() throws PaymentException {
        // Given
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20241201-001");
        order.setTotalAmount(new BigDecimal("59.98"));
        order.setStatus(OrderStatus.PENDING);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("59.98"));
        order.addPaymentTransaction(transaction);

        PaymentResult paymentResult = new PaymentResult(true, "txn_123", "Payment successful");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(paymentResult);

        // When
        orderProcessingService.processPaymentAsync(order);

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }

    @Test
    void processPaymentAsync_shouldNotUpdateOrderStatus_whenPaymentFails() throws PaymentException {
        // Given
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20241201-001");
        order.setTotalAmount(new BigDecimal("59.98"));
        order.setStatus(OrderStatus.PENDING);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("59.98"));
        order.addPaymentTransaction(transaction);

        PaymentException paymentException = new PaymentException("Payment declined");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenThrow(paymentException);

        // When
        orderProcessingService.processPaymentAsync(order);

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING); // Status unchanged
        verify(orderRepository).save(order);
    }

    @Test
    void processOrderCreated_shouldHandleMultipleOrderItems() {
        // Given
        OrderItemEvent item1 = new OrderItemEvent(
                UUID.randomUUID().toString(),
                "Product 1",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"));

        OrderItemEvent item2 = new OrderItemEvent(
                UUID.randomUUID().toString(),
                "Product 2",
                1,
                new BigDecimal("15.00"),
                new BigDecimal("15.00"));

        event = new OrderCreatedEvent(
                eventId.toString(),
                "ORDER_CREATED",
                "1.0",
                Instant.now().toString(),
                correlationId,
                UUID.randomUUID().toString(),
                "ORD-20241201-002",
                List.of(item1, item2),
                event.customer(),
                new BigDecimal("35.00"));

        when(processedEventRepository.existsByEventId(eventId.toString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // When
        orderProcessingService.processOrderCreated(event, correlationId, eventId.toString(), acknowledgment);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderItems()).hasSize(2);
        assertThat(savedOrder.getTotalAmount()).isEqualTo(new BigDecimal("35.00"));
    }
}
