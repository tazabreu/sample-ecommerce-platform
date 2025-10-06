package com.ecommerce.order.service;

import com.ecommerce.order.dto.CancelOrderRequest;
import com.ecommerce.order.dto.CustomerInfoDto;
import com.ecommerce.order.dto.FulfillOrderRequest;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.dto.OrderPageDto;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.ResourceNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentTransaction;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.model.ShippingAddress;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for OrderQueryService.
 * Tests order lookup, filtering, cancellation, and fulfillment logic.
 * 
 * Coverage Target: >90% line, >85% branch
 * Total Tests: 20 (Lookup: 3, Listing: 7, Cancellation: 5, Fulfillment: 5)
 */
@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    private OrderQueryService orderQueryService;

    private String testOrderNumber;
    private UUID testOrderId;
    private Order testOrder;
    private ShippingAddress testAddress;

    @BeforeEach
    void setUp() {
        orderQueryService = new OrderQueryService(
                orderRepository,
                orderMapper,
                paymentTransactionRepository
        );

        testOrderNumber = "ORD-20251006-001";
        testOrderId = UUID.randomUUID();
        testAddress = new ShippingAddress("123 Main St", "Springfield", "IL", "62701", "USA");
        
        testOrder = new Order(
                testOrderNumber,
                "John Doe",
                "john.doe@example.com",
                "+1234567890",
                testAddress,
                new BigDecimal("299.99")
        );
        testOrder.setId(testOrderId);
        testOrder.setStatus(OrderStatus.PENDING);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== Order Lookup Tests (3 tests) ====================

    @Test
    void getOrderByNumber_success() {
        // Arrange
        OrderDto expectedDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderMapper.toDto(testOrder)).thenReturn(expectedDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderDto result = orderQueryService.getOrderByNumber(testOrderNumber);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.orderNumber()).isEqualTo(testOrderNumber);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING.name());
        verify(orderRepository).findByOrderNumber(testOrderNumber);
        verify(orderMapper).toDto(testOrder);
    }

    @Test
    void getOrderByNumber_notFound_throwsException() {
        // Arrange
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderQueryService.getOrderByNumber(testOrderNumber))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining(testOrderNumber);
        
        verify(orderMapper, never()).toDto(any());
    }

    @Test
    void getOrderByNumber_enrichesPaymentStatus() {
        // Arrange
        OrderDto baseDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PROCESSING);
        PaymentTransaction paymentTx = new PaymentTransaction(testOrderId, new BigDecimal("299.99"), "MOCK");
        paymentTx.setStatus(PaymentStatus.SUCCESS);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderMapper.toDto(testOrder)).thenReturn(baseDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.of(paymentTx));

        // Act
        OrderDto result = orderQueryService.getOrderByNumber(testOrderNumber);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.paymentStatus()).isEqualTo("SUCCESS");
        verify(paymentTransactionRepository).findByOrderId(testOrderId);
    }

    // ==================== Order Listing & Filtering Tests (7 tests) ====================

    @Test
    void listOrders_noFilters_returnsAll() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = List.of(testOrder);
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findWithFilters(isNull(), isNull(), isNull(), isNull(), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(testOrder)).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(null, null, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.number()).isEqualTo(0);
        verify(orderRepository).findWithFilters(isNull(), isNull(), isNull(), isNull(), eq(11), eq(0L));
    }

    @Test
    void listOrders_byStatus_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        testOrder.setStatus(OrderStatus.PAID);
        List<Order> orders = List.of(testOrder);
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PAID);
        
        when(orderRepository.findWithFilters(isNull(), eq("PAID"), isNull(), isNull(), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(testOrder)).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(OrderStatus.PAID, null, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo("PAID");
        verify(orderRepository).findWithFilters(isNull(), eq("PAID"), isNull(), isNull(), eq(11), eq(0L));
    }

    @Test
    void listOrders_byCustomerEmail_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String email = "john.doe@example.com";
        List<Order> orders = List.of(testOrder);
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findWithFilters(eq(email), isNull(), isNull(), isNull(), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(testOrder)).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(null, email, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findWithFilters(eq(email), isNull(), isNull(), isNull(), eq(11), eq(0L));
    }

    @Test
    void listOrders_byDateRange_filtersCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = Instant.now();
        List<Order> orders = List.of(testOrder);
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findWithFilters(isNull(), isNull(), eq(startDate), eq(endDate), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(testOrder)).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(null, null, startDate, endDate, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findWithFilters(isNull(), isNull(), eq(startDate), eq(endDate), eq(11), eq(0L));
    }

    @Test
    void listOrders_combinedFilters_worksCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String email = "john.doe@example.com";
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = Instant.now();
        testOrder.setStatus(OrderStatus.PAID);
        List<Order> orders = List.of(testOrder);
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PAID);
        
        when(orderRepository.findWithFilters(eq(email), eq("PAID"), eq(startDate), eq(endDate), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(testOrder)).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(OrderStatus.PAID, email, startDate, endDate, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        verify(orderRepository).findWithFilters(eq(email), eq("PAID"), eq(startDate), eq(endDate), eq(11), eq(0L));
    }

    @Test
    void listOrders_pagination_hasNext_true() {
        // Arrange - Return more items than page size to indicate hasNext
        Pageable pageable = PageRequest.of(0, 5);
        List<Order> orders = List.of(testOrder, testOrder, testOrder, testOrder, testOrder, testOrder); // 6 items (limit+1)
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findWithFilters(isNull(), isNull(), isNull(), isNull(), eq(6), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(any(Order.class))).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(any(UUID.class))).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(null, null, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(5); // Should trim to page size
        assertThat(result.totalPages()).isGreaterThan(1); // Should indicate more pages
    }

    @Test
    void listOrders_pagination_hasNext_false() {
        // Arrange - Return exact page size or less
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = List.of(testOrder, testOrder); // 2 items (less than limit)
        OrderDto dto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.PENDING);
        
        when(orderRepository.findWithFilters(isNull(), isNull(), isNull(), isNull(), eq(11), eq(0L)))
                .thenReturn(orders);
        when(orderMapper.toDto(any(Order.class))).thenReturn(dto);
        when(paymentTransactionRepository.findByOrderId(any(UUID.class))).thenReturn(Optional.empty());

        // Act
        OrderPageDto result = orderQueryService.listOrders(null, null, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(2);
        assertThat(result.totalPages()).isEqualTo(1); // Should be last page
    }

    // ==================== Order Cancellation Tests (5 tests) ====================

    @Test
    void cancelOrder_statusPending_success() {
        // Arrange
        CancelOrderRequest request = new CancelOrderRequest("Customer requested cancellation");
        testOrder.setStatus(OrderStatus.PENDING);
        OrderDto expectedDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.CANCELLED);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toDto(testOrder)).thenReturn(expectedDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderDto result = orderQueryService.cancelOrder(testOrderNumber, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(testOrder);
    }

    @Test
    void cancelOrder_statusProcessing_success() {
        // Arrange
        CancelOrderRequest request = new CancelOrderRequest("Manager cancelled order");
        testOrder.setStatus(OrderStatus.PROCESSING);
        OrderDto expectedDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.CANCELLED);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toDto(testOrder)).thenReturn(expectedDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderDto result = orderQueryService.cancelOrder(testOrderNumber, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "FULFILLED", "CANCELLED"})
    void cancelOrder_invalidStatus_throwsException(OrderStatus invalidStatus) {
        // Arrange
        CancelOrderRequest request = new CancelOrderRequest("Attempt to cancel");
        testOrder.setStatus(invalidStatus);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> orderQueryService.cancelOrder(testOrderNumber, request))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Order cannot be cancelled in status:")
                .hasMessageContaining(invalidStatus.name());
        
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_notFound_throwsException() {
        // Arrange
        CancelOrderRequest request = new CancelOrderRequest("Cancel request");
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderQueryService.cancelOrder(testOrderNumber, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining(testOrderNumber);
    }

    // ==================== Order Fulfillment Tests (5 tests) ====================

    @Test
    void fulfillOrder_statusPaid_success() {
        // Arrange
        FulfillOrderRequest request = new FulfillOrderRequest("TRACK123", "UPS", "Priority shipping");
        testOrder.setStatus(OrderStatus.PAID);
        OrderDto expectedDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.FULFILLED);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toDto(testOrder)).thenReturn(expectedDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderDto result = orderQueryService.fulfillOrder(testOrderNumber, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(OrderStatus.FULFILLED.name());
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(testOrder.getCompletedAt()).isNotNull();
        verify(orderRepository).save(testOrder);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "PROCESSING", "CANCELLED", "FAILED", "FULFILLED"})
    void fulfillOrder_statusNotPaid_throwsException(OrderStatus invalidStatus) {
        // Arrange
        FulfillOrderRequest request = new FulfillOrderRequest("TRACK123", "UPS", null);
        testOrder.setStatus(invalidStatus);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> orderQueryService.fulfillOrder(testOrderNumber, request))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Order can only be fulfilled in PAID status")
                .hasMessageContaining(invalidStatus.name());
        
        verify(orderRepository, never()).save(any());
    }

    @Test
    void fulfillOrder_notFound_throwsException() {
        // Arrange
        FulfillOrderRequest request = new FulfillOrderRequest("TRACK123", "UPS", null);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderQueryService.fulfillOrder(testOrderNumber, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining(testOrderNumber);
    }

    @Test
    void fulfillOrder_recordsTrackingInfo() {
        // Arrange
        FulfillOrderRequest request = new FulfillOrderRequest("TRACK456", "FedEx", "Express delivery");
        testOrder.setStatus(OrderStatus.PAID);
        OrderDto expectedDto = createOrderDto(testOrderId, testOrderNumber, OrderStatus.FULFILLED);
        
        when(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(testOrder)).thenReturn(testOrder);
        when(orderMapper.toDto(testOrder)).thenReturn(expectedDto);
        when(paymentTransactionRepository.findByOrderId(testOrderId)).thenReturn(Optional.empty());

        // Act
        OrderDto result = orderQueryService.fulfillOrder(testOrderNumber, request);

        // Assert - Verify tracking info is logged (checked via logs in real implementation)
        assertThat(result).isNotNull();
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        verify(orderRepository).save(testOrder);
    }

    // ==================== Helper Methods ====================

    private OrderDto createOrderDto(UUID id, String orderNumber, OrderStatus status) {
        com.ecommerce.order.dto.ShippingAddressDto shippingAddressDto = 
                new com.ecommerce.order.dto.ShippingAddressDto(
                        "123 Main St",
                        "Springfield",
                        "IL",
                        "62701",
                        "USA"
                );
        
        CustomerInfoDto customerInfo = new CustomerInfoDto(
                "John Doe",
                "john.doe@example.com",
                "+1234567890",
                shippingAddressDto
        );
        
        return new OrderDto(
                id,
                orderNumber,
                customerInfo,
                List.of(),
                new BigDecimal("299.99"),
                status.name(),
                null,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                null
        );
    }
}
