CREATE TABLE IF NOT EXISTS chat_conversations (
	id TEXT NOT NULL PRIMARY KEY,
	title TEXT NOT NULL,
	createdAt INTEGER NOT NULL,
	updatedAt INTEGER NOT NULL,
	snapshotText TEXT,
	snapshotGeneratedAt INTEGER,
	modelId TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations (updatedAt);
CREATE TABLE IF NOT EXISTS chat_messages (
	id TEXT NOT NULL PRIMARY KEY,
	conversationId TEXT NOT NULL,
	role TEXT NOT NULL,
	content TEXT NOT NULL,
	toolCallsJson TEXT,
	toolCallId TEXT,
	modelId TEXT,
	createdAt INTEGER NOT NULL,
	FOREIGN KEY (conversationId) REFERENCES chat_conversations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_createdAt ON chat_messages (conversationId, createdAt);
