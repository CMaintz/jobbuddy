-- The application a generated CV and letter landed on, so the chat's card can open them for
-- review. Set by an approved item that wrote documents, also when it failed partway, since a CV
-- may already be there. Cleared if she deletes the application.
ALTER TABLE chat_plan_items
    ADD COLUMN result_application_id uuid,
    ADD CONSTRAINT chat_plan_items_result_application_id_fkey
        FOREIGN KEY (result_application_id) REFERENCES applications(id) ON DELETE SET NULL;
