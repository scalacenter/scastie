CREATE TABLE progresses (
    id BIGSERIAL PRIMARY KEY,
    snippet_id VARCHAR(128) NOT NULL,
    runtime_error TEXT,
    scala_js_content TEXT,
    scala_js_source_map_content TEXT,
    is_done BOOLEAN NOT NULL,
    is_timeout BOOLEAN NOT NULL,
    is_sbt_error BOOLEAN NOT NULL,
    is_forced_program_mode BOOLEAN NOT NULL,
    ts BIGINT,
    FOREIGN KEY (snippet_id) REFERENCES snippets(snippet_id) ON DELETE CASCADE
);