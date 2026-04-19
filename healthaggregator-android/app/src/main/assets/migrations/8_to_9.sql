-- 8 → 9: add user_narrative singleton table for "For my doctor" prose.
-- Single row (PK=1). Synced phone↔laptop via LWW on updatedAt so the text is
-- available to both the phone exports and the laptop Claude Max sessions.
CREATE TABLE IF NOT EXISTS user_narrative (
    id INTEGER PRIMARY KEY NOT NULL,
    text TEXT NOT NULL,
    updatedAt INTEGER NOT NULL
);
