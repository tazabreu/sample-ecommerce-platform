package com.ecommerce.order.service;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.PaymentTransaction;
import com.ecommerce.order.model.ProcessedEvent;
import com.ecommerce.order.model.ShippingAddress;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentTransactionRepository;
import com.ecommerce.order.repository.ProcessedEventRepository;
import com.ecommerce.order.payment.PaymentResult;
import com.ecommerce.shared.event.PaymentCompletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedServiceTest {

    @Mock
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    private PaymentCompletedService paymentCompletedService;

    private UUID orderId;
    private UUID paymentTransactionId;
    private String orderNumber;
    private String correlationId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        paymentCompletedService = new PaymentCompletedService(
                kafkaTemplate,
                orderRepository,
                processedEventRepository,
                paymentTransactionRepository
        );
        ReflectionTestUtils.setField(paymentCompletedService, "paymentCompletedTopic", "payments.completed");

        orderId = UUID.randomUUID();
        paymentTransactionId = UUID.randomUUID();
        orderNumber = "ORD-20241201-123";
        correlationId = "corr-123";
        amount = new BigDecimal("120.00");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publishPaymentCompleted_shouldSendEventWithCorrelationTracing() {
        Order order = new Order(
                orderNumber,
                "Jane Doe",
                "jane.doe@example.com",
                "+1234567890",
                shippingAddress(),
                amount
        );
        order.setId(orderId);
        order.markAsProcessing(); // Order should be in PROCESSING status for payment completion

        PaymentTransaction transaction = new PaymentTransaction(orderId, amount, "MOCK");
        transaction.setId(paymentTransactionId);

        PaymentResult result = PaymentResult.success("ext-123");

        CompletableFuture<SendResult<String, PaymentCompletedEvent>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentCompletedEvent.class))).thenReturn(future);

        MDC.put("correlationId", correlationId);

        paymentCompletedService.publishPaymentCompleted(order, transaction, result);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PaymentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("payments.completed");
        assertThat(keyCaptor.getValue()).isEqualTo(orderId.toString());

        PaymentCompletedEvent event = eventCaptor.getValue();
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.orderNumber()).isEqualTo(orderNumber);
        assertThat(event.paymentTransactionId()).isEqualTo(paymentTransactionId);
        assertThat(event.status()).isEqualTo("SUCCESS");
        assertThat(event.externalTransactionId()).isEqualTo("ext-123");
        assertThat(event.failureReason()).isNull();
    }

    @Test
    void processPaymentCompletedEvent_shouldUpdateStateOnSuccess() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                correlationId,
                orderId,
                orderNumber,
                paymentTransactionId,
                "SUCCESS",
                amount,
                "MOCK",
                "ext-123",
                null
        );

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);

        Order order = new Order(
                orderNumber,
                "Jane Doe",
                "jane.doe@example.com",
                "+1234567890",
                shippingAddress(),
                amount
        );
        order.setId(orderId);
        order.markAsProcessing(); // Order should be in PROCESSING status for payment completion

        PaymentTransaction transaction = new PaymentTransaction(orderId, amount, "MOCK");
        transaction.setId(paymentTransactionId);

        when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findById(paymentTransactionId)).thenReturn(Optional.of(transaction));

        paymentCompletedService.processPaymentCompletedEvent(event);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transaction.getExternalTransactionId()).isEqualTo("ext-123");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(paymentTransactionRepository).save(transaction);
        verify(orderRepository).save(order);

        ArgumentCaptor<ProcessedEvent> processedEventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertThat(processedEventCaptor.getValue().getEventId()).isEqualTo(event.eventId());
    }

    @Test
    void processPaymentCompletedEvent_shouldUpdateStateOnFailure() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                correlationId,
                orderId,
                orderNumber,
                paymentTransactionId,
                "FAILED",
                amount,
                "MOCK",
                null,
                "DECLINED"
        );

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);

        Order order = new Order(
                orderNumber,
                "Jane Doe",
                "jane.doe@example.com",
                "+1234567890",
                shippingAddress(),
                amount
        );
        order.setId(orderId);
        order.markAsProcessing(); // Order should be in PROCESSING status for payment completion

        PaymentTransaction transaction = new PaymentTransaction(orderId, amount, "MOCK");
        transaction.setId(paymentTransactionId);

        when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.findById(paymentTransactionId)).thenReturn(Optional.of(transaction));

        paymentCompletedService.processPaymentCompletedEvent(event);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transaction.getFailureReason()).isEqualTo("DECLINED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);

        verify(paymentTransactionRepository).save(transaction);
        verify(orderRepository).save(order);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void processPaymentCompletedEvent_shouldSkip_whenEventAlreadyProcessed() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                correlationId,
                orderId,
                orderNumber,
                paymentTransactionId,
                "SUCCESS",
                amount,
                "MOCK",
                "ext-123",
                null
        );

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(true);

        paymentCompletedService.processPaymentCompletedEvent(event);

        verify(orderRepository, never()).findByOrderNumber(anyString());
        verify(paymentTransactionRepository, never()).findById(any());
        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    private ShippingAddress shippingAddress() {
        return new ShippingAddress("123 Main St", "Anytown", "CA", "94105", "USA");
    }
}
