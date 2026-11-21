-- Date à laquelle l'agent a confirmé la tentative (CommandAck accepté ou premier message).
-- Sert à détecter un job disparu de sa machine sans fausse alerte : on laisse à l'agent le temps
-- de le déclarer dans ses heartbeats. NULL tant que la tentative n'est pas RUNNING.
ALTER TABLE assignment ADD COLUMN running_since TIMESTAMP WITH TIME ZONE;

-- Les tentatives déjà RUNNING au moment de la migration : leur date de création est la meilleure estimation.
UPDATE assignment SET running_since = created_at WHERE status = 'RUNNING';
