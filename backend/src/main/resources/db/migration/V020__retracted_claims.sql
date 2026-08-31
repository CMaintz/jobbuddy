-- Retracted claims.
--
-- Claims the user has struck out. The generation guard checks against these so a rejected
-- overstatement cannot quietly reappear in the next document.

CREATE TABLE retracted_claim (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    claim text NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT retracted_claim_pkey PRIMARY KEY (id),
    CONSTRAINT retracted_claim_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_retracted_claim_user ON public.retracted_claim USING btree (user_id);
