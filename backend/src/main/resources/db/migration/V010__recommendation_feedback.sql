-- User feedback on job recommendations to improve personalization
CREATE TABLE recommendation_feedback (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    feedback_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_job_feedback UNIQUE (user_id, job_id)
);

CREATE INDEX idx_recommendation_feedback_user_id ON recommendation_feedback(user_id);
