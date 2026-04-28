package com.banco.co.card.controller;

import com.banco.co.card.dto.ActivateCardRequestDto;
import com.banco.co.card.dto.BlockCardRequestDto;
import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.dto.CreateCardRequestDto;
import com.banco.co.card.dto.UpdateCardFeaturesRequestDto;
import com.banco.co.card.dto.UpdateCardLimitsRequestDto;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.service.ICardService;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardControllerTest {

    private MockMvc mockMvc;
    private ICardService cardService;
    private ObjectMapper objectMapper;
    private CardResponseDto sampleResponse;
    private CardSummaryDto sampleSummary;

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("user@banco.co", "");

    private static final String CARD_WRITE_SCOPE = "card:write";

    @BeforeEach
    void setUp() {
        cardService = mock(ICardService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CardController(cardService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();

        sampleResponse = new CardResponseDto(
                "CARD-2024-X7K9P2", "****-****-****-0366",
                CardType.DEBITO, CardBrand.VISA, CardTier.GOLD,
                CardStatus.ACTIVE, null, "ACC-001",
                LocalDateTime.now(), LocalDateTime.now().plusYears(6),
                null, null,
                new BigDecimal("1000000"), new BigDecimal("30000000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                true, true, false, 0L
        );

        sampleSummary = new CardSummaryDto(
                "CARD-2024-X7K9P2", "****-****-****-0366",
                CardType.DEBITO, CardBrand.VISA, CardTier.GOLD,
                CardStatus.ACTIVE, "ACC-001", LocalDateTime.now().plusYears(6)
        );
    }

    @Test
    void testCreateCard_ValidRequest_Returns201() throws Exception {
        CreateCardRequestDto dto = new CreateCardRequestDto(
                CardType.DEBITO, CardBrand.VISA, CardTier.GOLD, "ACC-001"
        );
        when(cardService.createCard(any(CreateCardRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardCode").value("CARD-2024-X7K9P2"));
    }

    @Test
    void testCreateCard_MissingCardType_Returns400() throws Exception {
        String invalidJson = """
                { "brand": "VISA", "tier": "GOLD", "accountCode": "ACC-001" }
                """;

        mockMvc.perform(post("/api/v1/cards")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetMyCards_Returns200() throws Exception {
        when(cardService.getMyCards(anyString())).thenReturn(List.of(sampleSummary));

        mockMvc.perform(get("/api/v1/cards").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardCode").value("CARD-2024-X7K9P2"));
    }

    @Test
    void testGetCardByCode_ValidCode_Returns200() throws Exception {
        when(cardService.getCardByCode(eq("CARD-2024-X7K9P2"), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/cards/CARD-2024-X7K9P2").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardCode").value("CARD-2024-X7K9P2"));
    }

    @Test
    void testActivateCard_ValidPin_Returns200() throws Exception {
        ActivateCardRequestDto dto = new ActivateCardRequestDto("1234");
        when(cardService.activateCard(eq("CARD-2024-X7K9P2"), any(ActivateCardRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/activate")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void testActivateCard_BlankPin_Returns400() throws Exception {
        String invalidJson = """
                { "pin": "" }
                """;

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/activate")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testBlockCard_ValidRequest_Returns200() throws Exception {
        BlockCardRequestDto dto = new BlockCardRequestDto("Suspicious activity");
        when(cardService.blockCard(eq("CARD-2024-X7K9P2"), any(BlockCardRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/block")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardCode").value("CARD-2024-X7K9P2"));
    }

    @Test
    void testReportStolen_Returns200() throws Exception {
        when(cardService.reportStolen(eq("CARD-2024-X7K9P2"), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/report-stolen").principal(USER))
                .andExpect(status().isOk());
    }

    @Test
    void testReportLost_Returns200() throws Exception {
        when(cardService.reportLost(eq("CARD-2024-X7K9P2"), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/report-lost").principal(USER))
                .andExpect(status().isOk());
    }

    @Test
    void testCloseCard_Returns200() throws Exception {
        when(cardService.closeCard(eq("CARD-2024-X7K9P2"), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards/CARD-2024-X7K9P2/close").principal(USER))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateLimits_Returns200() throws Exception {
        UpdateCardLimitsRequestDto dto = new UpdateCardLimitsRequestDto(
                new BigDecimal("2000000"), new BigDecimal("50000000")
        );
        when(cardService.updateLimits(eq("CARD-2024-X7K9P2"), any(UpdateCardLimitsRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/cards/CARD-2024-X7K9P2/limits")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateLimits_NegativeDailyLimit_Returns400() throws Exception {
        String invalidJson = """
                { "dailyLimit": -100, "monthlyLimit": 5000000 }
                """;

        mockMvc.perform(put("/api/v1/cards/CARD-2024-X7K9P2/limits")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateFeatures_Returns200() throws Exception {
        UpdateCardFeaturesRequestDto dto = new UpdateCardFeaturesRequestDto(true, false, true);
        when(cardService.updateFeatures(eq("CARD-2024-X7K9P2"), any(UpdateCardFeaturesRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/cards/CARD-2024-X7K9P2/features")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetCardsByAccount_Returns200() throws Exception {
        when(cardService.getMyCardsByAccount(eq("ACC-001"), anyString()))
                .thenReturn(List.of(sampleSummary));

        mockMvc.perform(get("/api/v1/cards/account/ACC-001").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardCode").value("CARD-2024-X7K9P2"));
    }

    @Test
    void testCreateCard_WithScopeCardWriteAuthority_Returns201() throws Exception {
        CreateCardRequestDto dto = new CreateCardRequestDto(
                CardType.DEBITO, CardBrand.VISA, CardTier.GOLD, "ACC-001"
        );
        when(cardService.createCard(any(CreateCardRequestDto.class), anyString()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/cards")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "user@banco.co",
                                "",
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(CARD_WRITE_SCOPE))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardCode").value("CARD-2024-X7K9P2"));
    }
}
