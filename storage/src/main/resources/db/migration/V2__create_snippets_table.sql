CREATE TABLE snippets (
    snippet_id VARCHAR(128) PRIMARY KEY,
    simple_snippet_id VARCHAR(64) NOT NULL,
    forked_snippet_id VARCHAR(128),
    username VARCHAR(64),
    inputs_hash VARCHAR(64) NOT NULL,
    is_showing_in_user_profile BOOLEAN NOT NULL DEFAULT true,
    scala_js_content TEXT NOT NULL,
    scala_js_source_map_content TEXT NOT NULL,
    time BIGINT NOT NULL,
    FOREIGN KEY (inputs_hash) REFERENCES inputs(hash)
);