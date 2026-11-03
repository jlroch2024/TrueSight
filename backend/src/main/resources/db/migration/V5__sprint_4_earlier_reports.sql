-- How many earlier annual reports the SEC had for a company when its history was last loaded (0, 1 or 2), so that the
-- count of earlier reports still to read survives a restart. Null: not asked yet.
ALTER TABLE companies ADD COLUMN earlier_reports INTEGER CHECK (earlier_reports BETWEEN 0 AND 2);
