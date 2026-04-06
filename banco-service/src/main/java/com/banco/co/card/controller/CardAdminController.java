package com.banco.co.card.controller;

import com.banco.co.card.dto.AdminChangeCardStatusRequestDto;
import com.banco.co.card.dto.AdminResetPinRequestDto;
import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.service.ICardService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/cards")
public class CardAdminController {

    private final ICardService cardService;

    public CardAdminController(ICardService cardService) {
        this.cardService = cardService;
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS ADMIN
    // ══════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyAuthority('card:write', 'SCOPE_card:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<Page<CardSummaryDto>> getAllByStatus(
            @RequestParam CardStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(cardService.adminGetAllByStatus(status, pageable));
    }

    // ══════════════════════════════════════════════════════════
    //  ACCIONES ADMIN
    // ══════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyAuthority('card:write', 'SCOPE_card:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{cardCode}/status")
    public ResponseEntity<CardResponseDto> changeStatus(
            @PathVariable String cardCode,
            @Valid @RequestBody AdminChangeCardStatusRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(cardService.adminChangeStatus(cardCode, dto, authentication.getName()));
    }

    @PreAuthorize("hasAnyAuthority('card:write', 'SCOPE_card:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{cardCode}/reset-pin")
    public ResponseEntity<Void> resetPin(
            @PathVariable String cardCode,
            @Valid @RequestBody AdminResetPinRequestDto dto,
            Authentication authentication) {
        cardService.adminResetPin(cardCode, dto, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
