package com.banco.co.account.adapter.out.jpa;

import com.banco.co.account.domain.model.Account;
import com.banco.co.card.model.Card;
import com.banco.co.envelope.model.Envelope;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MapStruct mapper: AccountEntity <-> Account (domain).
 * Cross-feature mappings:
 *   entity.user.id  → domain.userId
 *   entity.cards (List<Card>)       → domain.cardIds (List<UUID>)
 *   entity.envelopes (Set<Envelope>) → domain.envelopeIds (List<UUID>)
 */
@Mapper(componentModel = "spring")
interface AccountEntityMapper {

    @Mapping(target = "userId", expression = "java(entity.getUser() != null ? entity.getUser().getId() : null)")
    @Mapping(target = "cardIds", expression = "java(mapCardIds(entity.getCards()))")
    @Mapping(target = "envelopeIds", expression = "java(mapEnvelopeIds(entity.getEnvelopes()))")
    Account toDomain(AccountEntity entity);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "envelopes", ignore = true)
    AccountEntity toEntity(Account domain);

    default List<UUID> mapCardIds(List<Card> cards) {
        if (cards == null) {
            return List.of();
        }
        return cards.stream()
                .map(Card::getId)
                .collect(Collectors.toList());
    }

    default List<UUID> mapEnvelopeIds(Set<Envelope> envelopes) {
        if (envelopes == null) {
            return List.of();
        }
        return envelopes.stream()
                .map(Envelope::getId)
                .collect(Collectors.toList());
    }
}
