package me.zhengjie.modules.customer.order.rest;

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.dto.CustomerOrderInlineUpdateDto;
import me.zhengjie.modules.customer.order.service.CustomerOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerOrderControllerInlineUpdateTest {

    @Mock
    private CustomerOrderService orderService;

    @InjectMocks
    private CustomerOrderController controller;

    @Test
    void updateInline_acceptsExplicitNullValuesAndReturnsNoContent() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("field", "customMenuImage");
        request.put("value", null);
        request.put("expectedValue", null);

        ResponseEntity<Void> response = controller.updateInline(12L, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        ArgumentCaptor<CustomerOrderInlineUpdateDto> dtoCaptor =
                ArgumentCaptor.forClass(CustomerOrderInlineUpdateDto.class);
        verify(orderService).updateInline(org.mockito.ArgumentMatchers.eq(12L), dtoCaptor.capture());
        assertEquals("customMenuImage", dtoCaptor.getValue().getField());
        assertEquals(null, dtoCaptor.getValue().getValue());
        assertEquals(null, dtoCaptor.getValue().getExpectedValue());
    }

    @Test
    void updateInline_rejectsMissingAndAdditionalRequestKeys() {
        Map<String, Object> missingExpectedValue = new LinkedHashMap<>();
        missingExpectedValue.put("field", "status");
        missingExpectedValue.put("value", 4);
        BadRequestException missingException = assertThrows(BadRequestException.class,
                () -> controller.updateInline(12L, missingExpectedValue));
        assertEquals(HttpStatus.BAD_REQUEST.value(), missingException.getStatus());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("field", "status");
        request.put("value", 4);
        request.put("expectedValue", 1);
        request.put("unexpected", true);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> controller.updateInline(12L, request));

        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getStatus());
        verify(orderService, never()).updateInline(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.any(CustomerOrderInlineUpdateDto.class));
    }
}
