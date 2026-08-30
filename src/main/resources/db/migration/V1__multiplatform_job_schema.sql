CREATE TABLE IF NOT EXISTS analysis_jobs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255),
    platform VARCHAR(255),
    game_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(255),
    stage VARCHAR(255),
    depth INTEGER NOT NULL DEFAULT 12,
    completed_games INTEGER NOT NULL DEFAULT 0,
    cache_hits INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    result_json TEXT,
    coaching_report TEXT,
    personality_json TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS platform VARCHAR(255);
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS stage VARCHAR(255);
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS depth INTEGER;
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS completed_games INTEGER;
ALTER TABLE analysis_jobs ADD COLUMN IF NOT EXISTS cache_hits INTEGER;

UPDATE analysis_jobs SET platform = 'lichess' WHERE platform IS NULL;
UPDATE analysis_jobs
SET stage = CASE
    WHEN status = 'COMPLETED' THEN 'COMPLETED'
    WHEN status = 'FAILED' THEN 'FAILED'
    ELSE 'QUEUED'
END
WHERE stage IS NULL;
UPDATE analysis_jobs SET depth = 12 WHERE depth IS NULL;
UPDATE analysis_jobs
SET completed_games = CASE WHEN status = 'COMPLETED' THEN game_count ELSE 0 END
WHERE completed_games IS NULL;
UPDATE analysis_jobs SET cache_hits = 0 WHERE cache_hits IS NULL;

ALTER TABLE analysis_jobs ALTER COLUMN platform SET DEFAULT 'lichess';
ALTER TABLE analysis_jobs ALTER COLUMN platform SET NOT NULL;
ALTER TABLE analysis_jobs ALTER COLUMN stage SET DEFAULT 'QUEUED';
ALTER TABLE analysis_jobs ALTER COLUMN stage SET NOT NULL;
ALTER TABLE analysis_jobs ALTER COLUMN depth SET DEFAULT 12;
ALTER TABLE analysis_jobs ALTER COLUMN depth SET NOT NULL;
ALTER TABLE analysis_jobs ALTER COLUMN completed_games SET DEFAULT 0;
ALTER TABLE analysis_jobs ALTER COLUMN completed_games SET NOT NULL;
ALTER TABLE analysis_jobs ALTER COLUMN cache_hits SET DEFAULT 0;
ALTER TABLE analysis_jobs ALTER COLUMN cache_hits SET NOT NULL;
