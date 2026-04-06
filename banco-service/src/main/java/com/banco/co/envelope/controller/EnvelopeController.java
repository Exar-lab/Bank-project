package com.banco.co.envelope.controller;

import com.banco.co.envelope.dto.EnvelopeDepositDto;
import com.banco.co.envelope.dto.EnvelopeRequestDto;
import com.banco.co.envelope.dto.EnvelopeResponseDto;
import com.banco.co.envelope.dto.EnvelopeUpdateDto;
import com.banco.co.envelope.dto.EnvelopeWithdrawDto;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.service.IEnvelopeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/envelopes")
public class EnvelopeController {

    private final IEnvelopeService envelopeService;

    public EnvelopeController(IEnvelopeService envelopeService) {
        this.envelopeService = envelopeService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('envelope:write', 'SCOPE_envelope:write')")
    public ResponseEntity<EnvelopeResponseDto> create(
            @Valid @RequestBody EnvelopeRequestDto dto,
            Authentication authentication) {
        EnvelopeResponseDto response = envelopeService.create(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getMyEnvelopes(Authentication authentication) {
        return ResponseEntity.ok(envelopeService.getMyEnvelopes(authentication.getName()));
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<EnvelopeResponseDto> getByCode(
            @PathVariable String code,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.getActiveEnvelope(code, authentication.getName()));
    }

    @GetMapping("/account/{accountCode}")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getByAccount(
            @PathVariable String accountCode,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.getActiveAllByAccountCode(accountCode, authentication.getName()));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getByStatus(
            @PathVariable EnvelopeStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.findAllByStatus(status, authentication.getName()));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getByType(
            @PathVariable EnvelopeType type,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.findAllActiveByType(type, authentication.getName()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getByStatusAndAccount(
            @RequestParam EnvelopeStatus status,
            @RequestParam String accountCode,
            Authentication authentication) {
        return ResponseEntity.ok(
                envelopeService.findAllByStatusAndAccountCode(status, accountCode, authentication.getName())
        );
    }

    @GetMapping("/created-after")
    @PreAuthorize("hasAnyAuthority('envelope:read', 'SCOPE_envelope:read')")
    public ResponseEntity<List<EnvelopeResponseDto>> getByCreatedAfter(
            @RequestParam LocalDateTime createdAfter,
            @RequestParam String accountCode,
            Authentication authentication) {
        return ResponseEntity.ok(
                envelopeService.getActiveByCreatedAfter(createdAfter, accountCode, authentication.getName())
        );
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('envelope:write', 'SCOPE_envelope:write')")
    public ResponseEntity<EnvelopeResponseDto> update(
            @PathVariable String code,
            @Valid @RequestBody EnvelopeUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.update(dto, code, authentication.getName()));
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasAnyAuthority('envelope:write', 'SCOPE_envelope:write')")
    public ResponseEntity<EnvelopeResponseDto> deposit(
            @Valid @RequestBody EnvelopeDepositDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.deposit(dto, authentication.getName()));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyAuthority('envelope:write', 'SCOPE_envelope:write')")
    public ResponseEntity<EnvelopeResponseDto> withdraw(
            @Valid @RequestBody EnvelopeWithdrawDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(envelopeService.withdraw(dto, authentication.getName()));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('envelope:write', 'SCOPE_envelope:write')")
    public ResponseEntity<Void> delete(
            @PathVariable String code,
            Authentication authentication) {
        envelopeService.delete(code, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
