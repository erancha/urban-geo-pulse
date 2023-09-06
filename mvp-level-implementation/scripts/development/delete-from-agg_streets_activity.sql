	delete from agg_streets_activity
	where
		lastUpdateTimestamp between (now() AT TIME ZONE 'Israel' - interval '45 minutes') and now() AT TIME ZONE 'Israel'
;