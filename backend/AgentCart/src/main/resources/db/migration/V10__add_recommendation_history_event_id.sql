-- NULL preserves legacy histories and the direct, non-event save path.
-- Event saves always supply an ID; the DB arbitrates concurrent delivery.
ALTER TABLE recommendation_history
    ADD COLUMN event_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD CONSTRAINT uk_recommendation_history_event_id UNIQUE (event_id);
