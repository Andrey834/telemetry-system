-- Демо-данные: несколько устройств (ключи см. в scripts/seed-secrets.md, не в git) и один
-- admin-пользователь dashboard. ON CONFLICT DO NOTHING — на случай ручного повторного прогона.
INSERT INTO devices (device_id, name, group_name, api_key_hash) VALUES
    ('bus-1',    'Автобус №1',   'buses',  '6ee494c98366eb95d3147bf2f9de31bbedbbca37b0d975e48966e247ee5b9b6e'),
    ('bus-2',    'Автобус №2',   'buses',  '2598637d18f896328d68fd4939259f520328c2cb22873e38dbcffeeee1984bfc'),
    ('bus-3',    'Автобус №3',   'buses',  '0d5b59b4381fbc2e628ad66e1d43919df065d688352db90e7e828ec62722167d'),
    ('truck-1',  'Грузовик №1',  'trucks', '03f8fe4a9b9fa27119db5d974a81a4499277620cb20a7ad33653cf23a7133cf0'),
    ('truck-2',  'Грузовик №2',  'trucks', '7cf0373f9b61206ae820228f23e50e8bbb685a94bc27f0133b3447cc5e8496a0')
ON CONFLICT (device_id) DO NOTHING;

INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2y$10$UQ4OGbVAbutekN7uW64ZRujRoZlGoNRxlXiYfZT0AiA2evyOmUIli', 'ADMIN')
ON CONFLICT (username) DO NOTHING;
