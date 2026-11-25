CREATE TABLE user_outputs (
    id BIGSERIAL PRIMARY KEY,
    progress_id BIGINT NOT NULL,
    process_output TEXT NOT NULL,
    FOREIGN KEY (progress_id) REFERENCES progresses(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_outputs_progress_id ON user_outputs(progress_id);