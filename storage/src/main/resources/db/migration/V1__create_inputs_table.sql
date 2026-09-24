CREATE TABLE inputs (
    hash VARCHAR(64) PRIMARY KEY,
    input_type VARCHAR(32) NOT NULL,
    code TEXT NOT NULL,
    target VARCHAR(255) NOT NULL,
    libraries TEXT NOT NULL,
    is_worksheet BOOLEAN NOT NULL,
    sbt_config_extra TEXT,
    sbt_config_saved TEXT,
    sbt_plugins_config_extra TEXT,
    sbt_plugins_config_saved TEXT,
    libraries_from_list TEXT
);