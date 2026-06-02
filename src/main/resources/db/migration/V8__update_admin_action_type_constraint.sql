DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'admin_action_logs'
    ) THEN
        ALTER TABLE admin_action_logs
            DROP CONSTRAINT IF EXISTS admin_action_logs_action_type_check;
    END IF;
END $$;
