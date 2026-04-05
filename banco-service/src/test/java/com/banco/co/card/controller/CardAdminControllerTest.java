package com.banco.co.card.controller;

import com.banco.co.card.dto.AdminChangeCardStatusRequestDto;
import com.banco.co.card.dto.AdminResetPinRequestDto;
import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.service.ICardService;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardAdminControllerTest {

    private MockMvc mockMvc;
    private ICardService cardService;
    private ObjectMapper objectMapper;

    private static final UsernamePasswordAuthenticationToken ADMIN =
            new UsernamePasswordAuthenticationToken("admin@banco.co", "");

    @BeforeEach
    void setUp() {
        cardService = mock(ICardService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CardAdminController(cardService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void testGetAllByStatus_Returns200WithPage() throws Exception {
        CardSummaryDto summary = new CardSummaryDto(
                "CARD-001", "****-****-****-1234",
                CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC,
                CardStatus.ACTIVE, "ACC-001", LocalDateTime.now().plusYears(2)
        );

        when(cardService.adminGetAllByStatus(eq(CardStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/cards")
                        .param("status", "ACTIVE")
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].cardCode").value("CARD-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void testChangeStatus_ValidAdmin_Returns200() throws Exception {
        CardResponseDto response = buildCardResponse("CARD-001", CardStatus.BLOCKED);
        AdminChangeCardStatusRequestDto dto = new AdminChangeCardStatusRequestDto(
                CardStatus.BLOCKED, "Suspicious activity"
        );

        when(cardService.adminChangeStatus(eq("CARD-001"), any(AdminChangeCardStatusRequestDto.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/cards/CARD-001/status")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardCode").value("CARD-001"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void testChangeStatus_NullStatus_Returns400() throws Exception {
        String invalidJson = """
                { "reason": "some reason" }
                """;

        mockMvc.perform(put("/api/v1/admin/cards/CARD-001/status")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testResetPin_ValidAdmin_Returns204() throws Exception {
        AdminResetPinRequestDto dto = new AdminResetPinRequestDto("1234");

        doNothing().when(cardService).adminResetPin(
                eq("CARD-001"), any(AdminResetPinRequestDto.class), anyString()
        );

        mockMvc.perform(post("/api/v1/admin/cards/CARD-001/reset-pin")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }

    @Test
    void testResetPin_InvalidPinLength_Returns400() throws Exception {
        AdminResetPinRequestDto dto = new AdminResetPinRequestDto("123");

        mockMvc.perform(post("/api/v1/admin/cards/CARD-001/reset-pin")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testResetPin_BlankPin_Returns400() throws Exception {
        String invalidJson = """
                { "newPin": "" }
                """;

        mockMvc.perform(post("/api/v1/admin/cards/CARD-001/reset-pin")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    private CardResponseDto buildCardResponse(String cardCode, CardStatus status) {
        return new CardResponseDto(
                cardCode, "****-****-****-1234",
                CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC,
                status, status == CardStatus.BLOCKED ? "Suspicious activity" : null,
                "ACC-001",
                LocalDateTime.now(), LocalDateTime.now().plusYears(2),
                LocalDateTime.now(), null,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                true, true, false, 0L
        );
    }
}
