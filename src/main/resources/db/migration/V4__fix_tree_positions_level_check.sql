ALTER TABLE tree_positions
    DROP CONSTRAINT tree_positions_level_check;

ALTER TABLE tree_positions
    ADD CONSTRAINT tree_positions_level_check CHECK (level BETWEEN 0 AND 4);
