-- Agrega los factores iniciales solo cuando la configuración existente aún no los contiene.
-- No modifica históricos, cotizaciones actuales ni valorBase/factorEscala.
UPDATE estrategias_valorizacion
SET configuracion_json = jsonb_set(
        configuracion_json::jsonb,
        '{factoresMercado}',
        '{"DL":1.15,"MC":1.05,"DF":0.95,"PO":0.90}'::jsonb,
        true
    )::text
WHERE id = 'POSITIONAL'
  AND version = 1
  AND NOT (configuracion_json::jsonb ? 'factoresMercado');
