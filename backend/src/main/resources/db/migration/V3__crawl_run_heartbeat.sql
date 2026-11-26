-- Running crawls update heartbeat_at every few seconds. A RUNNING crawl whose heartbeat is older than a few
-- minutes belongs to a process that died, and is marked FAILED.
ALTER TABLE crawl_runs ADD COLUMN heartbeat_at TIMESTAMPTZ;
