-- Changes the chat proposed, and what she decided and what happened. The chat model can only
-- propose; an item runs when she approves it in the app. These rows are also the record of what
-- the chat changed, so they outlive the chat they came from (chat_id goes NULL) and go only with
-- her account.
CREATE TABLE chat_plans (
    id         uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id    uuid NOT NULL,
    chat_id    uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chat_plans_pkey PRIMARY KEY (id),
    CONSTRAINT chat_plans_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chat_plans_chat_id_fkey FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE SET NULL
);

CREATE INDEX idx_chat_plans_chat ON chat_plans (chat_id);

-- details: ids, typed values and the posting's title and company as checked when proposed.
-- state moves PENDING -> REJECTED, or PENDING -> RUNNING -> DONE | FAILED, and never back.
CREATE TABLE chat_plan_items (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    plan_id     uuid NOT NULL,
    position    integer NOT NULL,
    kind        varchar(30) NOT NULL,
    details     jsonb NOT NULL,
    state       varchar(10) NOT NULL,
    failure     varchar(30),
    decided_at  timestamp with time zone,
    finished_at timestamp with time zone,
    CONSTRAINT chat_plan_items_pkey PRIMARY KEY (id),
    CONSTRAINT chat_plan_items_position_key UNIQUE (plan_id, position),
    CONSTRAINT chat_plan_items_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES chat_plans(id) ON DELETE CASCADE,
    CONSTRAINT chat_plan_items_state_check
        CHECK (state IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'REJECTED'))
);

CREATE INDEX idx_chat_plan_items_state ON chat_plan_items (plan_id, state);

-- The plans an answer proposed, by id; their cards are read from chat_plans every time.
ALTER TABLE chat_messages
    ADD COLUMN plan_ids jsonb NOT NULL DEFAULT '[]'::jsonb;
