CREATE TABLE instrumentations (
    id BIGSERIAL PRIMARY KEY,
    progress_id BIGINT NOT NULL,
    position TEXT NOT NULL,
    render TEXT NOT NULL,
    FOREIGN KEY (progress_id) REFERENCES progresses(id) ON DELETE CASCADE
);