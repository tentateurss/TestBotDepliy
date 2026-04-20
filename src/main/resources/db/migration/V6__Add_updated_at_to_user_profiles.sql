-- Add updated_at column to user_profiles table
ALTER TABLE user_profiles ADD COLUMN updated_at TEXT DEFAULT CURRENT_TIMESTAMP;

-- Update existing records to have updated_at same as created_at
UPDATE user_profiles SET updated_at = created_at WHERE updated_at IS NULL;
