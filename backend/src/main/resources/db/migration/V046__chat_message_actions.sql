-- The buttons an answer offered (open a posting, step through several), as the model proposed
-- them: kind and posting ids. They are checked again against the postings every time the chat is
-- read, so this keeps no titles.
ALTER TABLE chat_messages
    ADD COLUMN actions jsonb NOT NULL DEFAULT '[]'::jsonb;
