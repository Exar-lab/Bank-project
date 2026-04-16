package com.banco.co.card.controller;

import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.service.ICardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CardController.class)
@ContextConfiguration(classes = {
        CardControllerSecuritySliceWebMvcTest.TestApplication.class,
        CardController.class,
        CardControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
@Import({
        CardControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
class CardControllerSecuritySliceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ICardService cardService;

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_card:read")
    void testGetCardByCode_WithReadScope_Returns200() throws Exception {
        when(cardService.getCardByCode(eq("CARD-2024-X7K9P2"), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/cards/CARD-2024-X7K9P2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "card:read")
    void testGetCardByCode_WithLegacyReadAuthority_Returns200() throws Exception {
        when(cardService.getCardByCode(eq("CARD-2024-X7K9P2"), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/cards/CARD-2024-X7K9P2"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_user:read")
    void testGetCardByCode_WithoutCardReadScope_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/cards/CARD-2024-X7K9P2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetCardByCode_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/cards/CARD-2024-X7K9P2"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean("cardControllerTestSecurityFilterChain")
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) -> response.sendError(401))
                            .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(403))
                    )
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);

            return http.build();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    private CardResponseDto sampleResponse() {
        return new CardResponseDto(
                "CARD-2024-X7K9P2", "****-****-****-0366",
                CardType.DEBITO, CardBrand.VISA, CardTier.GOLD,
                CardStatus.ACTIVE, null, "ACC-001",
                LocalDateTime.now(), LocalDateTime.now().plusYears(6),
                null, null,
                new BigDecimal("1000000"), new BigDecimal("30000000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                true, true, false, 0L
        );
    }
}
