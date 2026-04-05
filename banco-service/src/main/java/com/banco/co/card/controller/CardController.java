package com.banco.co.card.controller;

import com.banco.co.card.dto.ActivateCardRequestDto;
import com.banco.co.card.dto.BlockCardRequestDto;
import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.dto.CreateCardRequestDto;
import com.banco.co.card.dto.UpdateCardFeaturesRequestDto;
import com.banco.co.card.dto.UpdateCardLimitsRequestDto;
import com.banco.co.card.service.ICardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/cards")
public class CardController {

    private final ICardService cardService;

    public CardController(ICardService cardService) {
        this.cardService = cardService;
    }

    @PreAuthorize("hasAuthority('card:create')")
    @PostMapping
    public ResponseEntity<CardResponseDto> createCard(
            @Valid @RequestBody CreateCardRequestDto dto,
            Authentication authentication) {
        CardResponseDto response = cardService.createCard(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAuthority('card:read')")
    @GetMapping
    public ResponseEntity<List<CardSummaryDto>> getMyCards(Authentication authentication) {
        return ResponseEntity.ok(cardService.getMyCards(authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:read')")
    @GetMapping("/account/{accountCode}")
    public ResponseEntity<List<CardSummaryDto>> getCardsByAccount(
            @PathVariable String accountCode,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.getMyCardsByAccount(accountCode, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:read')")
    @GetMapping("/{cardCode}")
    public ResponseEntity<CardResponseDto> getCardByCode(
            @PathVariable String cardCode,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.getCardByCode(cardCode, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:create')")
    @PostMapping("/{cardCode}/activate")
    public ResponseEntity<CardResponseDto> activateCard(
            @PathVariable String cardCode,
            @Valid @RequestBody ActivateCardRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.activateCard(cardCode, dto, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:block')")
    @PostMapping("/{cardCode}/block")
    public ResponseEntity<CardResponseDto> blockCard(
            @PathVariable String cardCode,
            @Valid @RequestBody BlockCardRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.blockCard(cardCode, dto, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:block')")
    @PostMapping("/{cardCode}/report-stolen")
    public ResponseEntity<CardResponseDto> reportStolen(
            @PathVariable String cardCode,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.reportStolen(cardCode, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:block')")
    @PostMapping("/{cardCode}/report-lost")
    public ResponseEntity<CardResponseDto> reportLost(
            @PathVariable String cardCode,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.reportLost(cardCode, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:block')")
    @PostMapping("/{cardCode}/close")
    public ResponseEntity<CardResponseDto> closeCard(
            @PathVariable String cardCode,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.closeCard(cardCode, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:limit:update')")
    @PutMapping("/{cardCode}/limits")
    public ResponseEntity<CardResponseDto> updateLimits(
            @PathVariable String cardCode,
            @Valid @RequestBody UpdateCardLimitsRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.updateLimits(cardCode, dto, authentication.getName()));
    }

    @PreAuthorize("hasAuthority('card:create')")
    @PutMapping("/{cardCode}/features")
    public ResponseEntity<CardResponseDto> updateFeatures(
            @PathVariable String cardCode,
            @Valid @RequestBody UpdateCardFeaturesRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.updateFeatures(cardCode, dto, authentication.getName()));
    }
}
