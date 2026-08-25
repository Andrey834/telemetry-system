-- Возможность деактивировать устройство (не удаляя реестр/историю) — деактивированное не проходит
-- API-key проверку в ingestion-service и не показывается на live-карте/списке dashboard.
ALTER TABLE devices ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
