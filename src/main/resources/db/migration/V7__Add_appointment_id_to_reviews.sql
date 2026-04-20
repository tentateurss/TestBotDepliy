-- Add appointment_id column to reviews table
ALTER TABLE reviews ADD COLUMN appointment_id INTEGER;

-- Create index for better performance
CREATE INDEX IF NOT EXISTS idx_reviews_appointment_id ON reviews(appointment_id);
