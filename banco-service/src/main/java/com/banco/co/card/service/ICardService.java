package com.banco.co.card.service;

import com.banco.co.card.dto.*;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.model.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ICardService {

    // INTERNAL — used by TransactionService (do not remove or modify)
    Card findCardWithAccountByCardCode(String cardCode);

    // CUSTOMER USE CASES
    CardResponseDto createCard(CreateCardRequestDto dto, String userEmail);
    List<CardSummaryDto> getMyCards(String userEmail);
    List<CardSummaryDto> getMyCardsByAccount(String accountCode, String userEmail);
    CardResponseDto getCardByCode(String cardCode, String userEmail);
    CardResponseDto activateCard(String cardCode, ActivateCardRequestDto dto, String userEmail);
    CardResponseDto blockCard(String cardCode, BlockCardRequestDto dto, String userEmail);
    CardResponseDto reportStolen(String cardCode, String userEmail);
    CardResponseDto reportLost(String cardCode, String userEmail);
    CardResponseDto closeCard(String cardCode, String userEmail);
    CardResponseDto updateLimits(String cardCode, UpdateCardLimitsRequestDto dto, String userEmail);
    CardResponseDto updateFeatures(String cardCode, UpdateCardFeaturesRequestDto dto, String userEmail);

    // ADMIN USE CASES
    Page<CardSummaryDto> adminGetAllByStatus(CardStatus status, Pageable pageable);
    CardResponseDto adminChangeStatus(String cardCode, AdminChangeCardStatusRequestDto dto, String adminEmail);
    void adminResetPin(String cardCode, AdminResetPinRequestDto dto, String adminEmail);
}
