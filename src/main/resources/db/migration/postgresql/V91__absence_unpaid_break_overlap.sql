ALTER TABLE absence_periods ADD COLUMN unpaid_break_overlap_minutes INTEGER;
ALTER TABLE absence_periods ADD CONSTRAINT ck_absence_unpaid_break_overlap CHECK (unpaid_break_overlap_minutes IS NULL OR (unpaid_break_overlap_minutes BETWEEN 0 AND 1440 AND coverage = 'PARTIAL'));
