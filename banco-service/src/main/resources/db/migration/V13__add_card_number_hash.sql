ALTER TABLE cards
    DROP INDEX uq_cards_card_number,
    ADD COLUMN card_number_hash VARCHAR(64) NOT NULL,
    ADD UNIQUE INDEX uk_cards_card_number_hash (card_number_hash);
