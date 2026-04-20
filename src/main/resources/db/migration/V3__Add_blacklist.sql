-- Add blacklist table for blocking problematic clients

CREATE TABLE IF NOT EXISTS blacklist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE,
    user_name TEXT NOT NULL,
    phone TEXT,
    reason TEXT NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    created_by INTEGER NOT NULL
);

-- Create index for fast lookup
CREATE INDEX IF NOT EXISTS idx_blacklist_user_id ON blacklist(user_id);
