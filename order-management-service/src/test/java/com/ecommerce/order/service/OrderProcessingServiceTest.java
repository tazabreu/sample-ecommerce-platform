package com.ecommerce.order.service;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.PaymentTransaction;
import com.ecommerce.order.model.ProcessedEvent;
import com.ecommerce.order.payment.PaymentException;
import com.ecommerce.order.payment.PaymentRequest;
import com.ecommerce.order.payment.PaymentResult;
import com.ecommerce.order.payment.PaymentService;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.ProcessedEventRepository;
import com.ecommerce.shared.event.CustomerEvent;
import com.ecommerce.shared.event.OrderCreatedEvent;
import com.ecommerce.shared.event.OrderItemEvent;
import com.ecommerce.shared.event.ShippingAddressEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderProcessingService} covering order creation and payment orchestration.
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
    private PaymentCompletedService paymentCompletedService;

    private OrderProcessingService orderProcessingService;

    private OrderCreatedEvent event;
    private UUID eventId;
    private UUID orderId;
    private UUID cartId;
    private UUID productId;
    private String correlationId;
    private String productSku;
    private String productName;
    private String orderNumber;
    private BigDecimal itemPrice;
    private BigDecimal itemSubtotal;
    private BigDecimal orderSubtotal;
    private CustomerEvent customer;

    @BeforeEach
    void setUp() {
        orderProcessingService = spy(new OrderProcessingService(
                orderRepository,
                processedEventRepository,
                paymentService,
                paymentCompletedService
        ));
        lenient().doNothing().when(orderProcessingService).processPaymentAsync(any(Order.class));

        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        cartId = UUID.randomUUID();
        productId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();
        productSku = "SKU-12345";
        productName = "Test Product";
        orderNumber = "ORD-20241201-001";
        itemPrice = new BigDecimal("29.99");
        itemSubtotal = new BigDecimal("59.98");
        orderSubtotal = itemSubtotal;

        ShippingAddressEvent shippingAddress = new ShippingAddressEvent(
                "123 Main St",
                "Anytown",
                "CA",
                "12345",
                "USA"
        );

        customer = new CustomerEvent(
                "John Doe",
                "john.doe@example.com",
                "+1234567890",
                shippingAddress
        );

        OrderItemEvent item = new OrderItemEvent(
                productId,
                productSku,
                productName,
                2,
                itemPrice,
                itemSubtotal
        );

        event = new OrderCreatedEvent(
                eventId,
                "ORDER_CREATED",
                "1.0",
                Instant.now(),
                correlationId,
                orderId,
                orderNumber,
                customer,
                List.of(item),
                orderSubtotal,
                cartId
        );
    }

    @Test
    void processOrderCreatedEvent_shouldCreateOrderSuccessfully() {
        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        mockOrderRepositorySave();

        orderProcessingService.processOrderCreatedEvent(event);

        ArgumentCaptor<ProcessedEvent> processedEventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertThat(processedEventCaptor.getValue().getEventId()).isEqualTo(event.eventId());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCustomerEmail()).isEqualTo(customer.email());
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(savedOrder.getShippingAddress()).containsEntry("street", "123 Main St");
        assertThat(savedOrder.getPaymentTransaction()).isNotNull();
    }

    @Test
    void processOrderCreatedEvent_shouldHandleIdempotency_whenEventAlreadyProcessed() {
        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(true);

        orderProcessingService.processOrderCreatedEvent(event);

        verify(orderRepository, never()).save(any(Order.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        verify(orderProcessingService, never()).processPaymentAsync(any(Order.class));
    }

    @Test
    void processOrderCreatedEvent_shouldCreatePaymentTransaction() {
        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        mockOrderRepositorySave();

        orderProcessingService.processOrderCreatedEvent(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        PaymentTransaction transaction = savedOrder.getPaymentTransaction();
        assertThat(transaction).isNotNull();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getAmount()).isEqualByComparingTo(orderSubtotal);
        assertThat(transaction.getPaymentMethod()).isEqualTo("MOCK");
        assertThat(transaction.getOrder()).isEqualTo(savedOrder);
    }

    @Test
    void processOrderCreatedEvent_shouldMapOrderItemsCorrectly() {
        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        mockOrderRepositorySave();

        orderProcessingService.processOrderCreatedEvent(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        List<OrderItem> items = savedOrder.getItems();
        assertThat(items).hasSize(1);
        OrderItem orderItem = items.get(0);

        assertThat(orderItem.getProductId()).isEqualTo(productId);
        assertThat(orderItem.getProductSku()).isEqualTo(productSku);
        assertThat(orderItem.getProductName()).isEqualTo(productName);
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getPriceSnapshot()).isEqualByComparingTo(itemPrice);
        assertThat(orderItem.getSubtotal()).isEqualByComparingTo(itemSubtotal);
    }

    @Test
    void processOrderCreatedEvent_shouldMapCustomerInfoCorrectly() {
        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        mockOrderRepositorySave();

        orderProcessingService.processOrderCreatedEvent(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getCustomerName()).isEqualTo(customer.name());
        assertThat(savedOrder.getCustomerEmail()).isEqualTo(customer.email());
        assertThat(savedOrder.getCustomerPhone()).isEqualTo(customer.phone());
        assertThat(savedOrder.getShippingAddress()).containsEntry("city", "Anytown");
        assertThat(savedOrder.getShippingAddress()).containsEntry("postalCode", "12345");
        assertThat(savedOrder.getShippingAddress()).containsEntry("country", "USA");
    }

    @Test
    void processOrderCreatedEvent_shouldHandleMultipleOrderItems() {
        OrderItemEvent item1 = new OrderItemEvent(
                UUID.randomUUID(),
                "SKU-1",
                "Product 1",
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00")
        );

        OrderItemEvent item2 = new OrderItemEvent(
                UUID.randomUUID(),
                "SKU-2",
                "Product 2",
                1,
                new BigDecimal("15.00"),
                new BigDecimal("15.00")
        );

        OrderCreatedEvent multiItemEvent = new OrderCreatedEvent(
                UUID.randomUUID(),
                "ORDER_CREATED",
                "1.0",
                Instant.now(),
                UUID.randomUUID().toString(),
                orderId,
                "ORD-20241201-002",
                customer,
                List.of(item1, item2),
                new BigDecimal("35.00"),
                cartId
        );

        when(processedEventRepository.existsByEventId(multiItemEvent.eventId())).thenReturn(false);
        mockOrderRepositorySave();

        orderProcessingService.processOrderCreatedEvent(multiItemEvent);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getItems()).hasSize(2);
        assertThat(savedOrder.getSubtotal()).isEqualByComparingTo(new BigDecimal("35.00"));
    }

    @Test
    void processPaymentAsync_shouldProcessPaymentSuccessfully() throws PaymentException {
        OrderProcessingService service = new OrderProcessingService(
                orderRepository,
                processedEventRepository,
                paymentService,
                paymentCompletedService
        );

        Order order = new Order(
                orderNumber,
                customer.name(),
                customer.email(),
                customer.phone(),
                Map.of(
                        "street", "123 Main St",
                        "city", "Anytown",
                        "state", "CA",
                        "postalCode", "12345",
                        "country", "USA"
                ),
                orderSubtotal
        );
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        PaymentResult paymentResult = PaymentResult.success("txn_123");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(paymentResult);

        service.processPaymentAsync(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        ArgumentCaptor<PaymentRequest> paymentRequestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentService).processPayment(paymentRequestCaptor.capture());
        PaymentRequest capturedRequest = paymentRequestCaptor.getValue();
        assertThat(capturedRequest.orderId()).isEqualTo(orderId);
        assertThat(capturedRequest.orderNumber()).isEqualTo(orderNumber);
        assertThat(capturedRequest.amount()).isEqualByComparingTo(orderSubtotal);

        verify(orderRepository).save(order);
        verify(paymentCompletedService).publishPaymentCompleted(order, paymentResult);
    }

    @Test
    void processPaymentAsync_shouldHandlePaymentFailure() throws PaymentException {
        OrderProcessingService service = new OrderProcessingService(
                orderRepository,
                processedEventRepository,
                paymentService,
                paymentCompletedService
        );

        Order order = new Order(
                orderNumber,
                customer.name(),
                customer.email(),
                customer.phone(),
                Map.of(
                        "street", "123 Main St",
                        "city", "Anytown",
                        "state", "CA",
                        "postalCode", "12345",
                        "country", "USA"
                ),
                orderSubtotal
        );
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        PaymentException paymentException = new PaymentException("Payment gateway timeout");
        when(paymentService.processPayment(any(PaymentRequest.class))).thenThrow(paymentException);

        service.processPaymentAsync(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        ArgumentCaptor<PaymentResult> paymentResultCaptor = ArgumentCaptor.forClass(PaymentResult.class);
        verify(paymentCompletedService).publishPaymentCompleted(eq(order), paymentResultCaptor.capture());
        PaymentResult result = paymentResultCaptor.getValue();
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Payment gateway timeout");

        verify(orderRepository).save(order);
        verify(paymentService).processPayment(any(PaymentRequest.class));
    }

    private void mockOrderRepositorySave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            }
            return order;
        });
    }
}
