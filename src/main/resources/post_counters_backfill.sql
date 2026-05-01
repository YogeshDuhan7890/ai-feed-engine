ALTER TABLE posts ADD COLUMN IF NOT EXISTS like_count bigint NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS view_count bigint NOT NULL DEFAULT 0;

UPDATE posts p
SET like_count = counts.like_count
FROM (
	SELECT post_id, COUNT(*) AS like_count
	FROM engagements
	WHERE type = 'LIKE'
	GROUP BY post_id
) counts
WHERE p.id = counts.post_id;

UPDATE posts p
SET view_count = counts.view_count
FROM (
	SELECT post_id, COUNT(*) AS view_count
	FROM engagements
	WHERE type = 'WATCH'
	GROUP BY post_id
) counts
WHERE p.id = counts.post_id;
