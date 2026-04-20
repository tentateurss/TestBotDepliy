-- Fix work_days table: ensure all records have valid time_slots
-- This migration fixes any potential data inconsistencies

-- Update any NULL or empty time_slots with default slots
UPDATE work_days 
SET time_slots = '["10:00","11:00","12:00","13:00","14:00","15:00","16:00","17:00","18:00"]'
WHERE time_slots IS NULL OR time_slots = '' OR time_slots = '[]';
