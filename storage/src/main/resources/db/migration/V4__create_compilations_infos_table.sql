CREATE TABLE compilation_infos (
    id BIGSERIAL PRIMARY KEY,
    progress_id BIGINT NOT NULL,
    severity VARCHAR(32) NOT NULL,
    line INT,
    end_line INT,
    start_column INT,
    end_column INT,
    message TEXT NOT NULL,
    FOREIGN KEY (progress_id) REFERENCES progresses(id) ON DELETE CASCADE
);