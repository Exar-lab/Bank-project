package com.banco.co.transaction.adapter.out.jpa;

import com.banco.co.transaction.domain.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper: TransactionEntity <-> Transaction (domain).
 * Cross-feature FK mappings:
 *   entity.fromAccount.id   → domain.fromAccountId
 *   entity.toAccount.id     → domain.toAccountId
 *   entity.card.id          → domain.cardId
 *   entity.envelope.id      → domain.envelopeId
 *   entity.originalTransaction.id → domain.originalTransactionId
 *
 * toEntity ignores all FK fields (fromAccount, toAccount, card, envelope, originalTransaction)
 * — the adapter resolves and sets them by FK lookup before calling jpaRepo.save().
 */
@Mapper(componentModel = "spring")
interface TransactionEntityMapper {

    @Mapping(target = "fromAccountId", expression = "java(entity.getFromAccount() != null ? entity.getFromAccount().getId() : null)")
    @Mapping(target = "toAccountId", expression = "java(entity.getToAccount() != null ? entity.getToAccount().getId() : null)")
    @Mapping(target = "fromAccountCode", expression = "java(entity.getFromAccount() != null ? entity.getFromAccount().getAccountCode() : null)")
    @Mapping(target = "toAccountCode", expression = "java(entity.getToAccount() != null ? entity.getToAccount().getAccountCode() : null)")
    @Mapping(target = "cardId", expression = "java(entity.getCard() != null ? entity.getCard().getId() : null)")
    @Mapping(target = "envelopeId", expression = "java(entity.getEnvelope() != null ? entity.getEnvelope().getId() : null)")
    @Mapping(target = "originalTransactionId", expression = "java(entity.getOriginalTransaction() != null ? entity.getOriginalTransaction().getId() : null)")
    Transaction toDomain(TransactionEntity entity);

    @Mapping(target = "fromAccount", ignore = true)
    @Mapping(target = "toAccount", ignore = true)
    @Mapping(target = "card", ignore = true)
    @Mapping(target = "envelope", ignore = true)
    @Mapping(target = "originalTransaction", ignore = true)
    TransactionEntity toEntity(Transaction domain);
}
