# Предрелизный checklist

## Назначение

Этот checklist нужен перед:
- сдачей проекта
- выкладкой на Debian-сервер
- обновлением боевого окружения

## Быстрая проверка одной командой

Базовая проверка:

```bash
./scripts/release-check.sh
```

Полная проверка вместе с реальным backup:

```bash
./scripts/release-check.sh --with-backup
```

## Что проверяет release-check

Скрипт проверяет:
- наличие `.env`
- замену дефолтных `POSTGRES_PASSWORD` и `APP_JWT_SECRET`
- отсутствие дефолтного `admin / 00000` в health-check конфигурации
- валидность `docker-compose.yml`
- что `frontend`, `backend`, `postgres` реально запущены
- что frontend отвечает по HTTP
- что security headers реально отдаются nginx
- что health-check и smoke-test проходят
- что backup cron установлен
- при флаге `--with-backup` дополнительно проверяет реальное создание backup

## Ручной чек-лист

Перед релизом стоит отдельно глазами проверить:

1. Конфигурация
- `.env` создан из `.env.example`
- `POSTGRES_PASSWORD` изменён
- `APP_JWT_SECRET` изменён
- пароль администратора изменён с дефолтного
- `APP_HEALTHCHECK_PASSWORD` синхронизирован с актуальным паролем администратора
- backend и PostgreSQL не торчат наружу без необходимости

2. Контейнеры
- `docker compose ps` не показывает упавшие сервисы
- postgres имеет `healthy`
- frontend открывается снаружи

3. Функциональность
- вход под администратором работает
- каталог открывается
- операции по картриджам выполняются
- отчёты открываются и скачиваются
- журнал действий пишет записи

4. Безопасность
- стандартный браузерный Basic Auth popup не появляется
- JWT-сессия продлевается при активности
- авто-logout при бездействии работает
- чувствительные действия требуют подтверждения

5. Резервное копирование
- `./scripts/backup-db.sh` создаёт файл в `backups/`
- `./scripts/install-backup-cron.sh` установлен на сервере
- есть понятная инструкция по restore

6. Документация
- актуален `README.md`
- есть руководство пользователя
- есть руководство администратора
- есть Debian deploy guide
- есть архитектурное описание

## Рекомендуемая последовательность перед продом

```bash
cp .env.example .env
./scripts/prepare-release-env.sh --write --rotate-admin-password --install-backup-cron
docker compose up -d --build
./scripts/check-health.sh
./scripts/smoke-test.sh
./scripts/release-check.sh --with-backup
```

## После успешной проверки

Можно считать, что система готова к передаче или выкладке, если:
- нет `FAIL`
- все критичные сценарии проходят
- backup реально создаётся
- конфигурация не использует дефолтные секреты
