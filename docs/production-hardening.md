# Подготовка продового окружения

## Назначение

Этот шаг нужен перед выкладкой на Debian-сервер, чтобы не оставлять:
- дефолтный пароль PostgreSQL
- дефолтный JWT secret
- дефолтный `admin / 00000`

## Быстрый сценарий

Сухой прогон без изменений:

```bash
./scripts/prepare-release-env.sh --rotate-admin-password
```

Применение:

```bash
./scripts/prepare-release-env.sh --write --rotate-admin-password --install-backup-cron
docker compose up -d --build
```

## Что делает скрипт

Скрипт:
- создаёт backup текущего `.env`
- генерирует новый `POSTGRES_PASSWORD`
- генерирует новый `APP_JWT_SECRET`
- при флаге `--rotate-admin-password` меняет пароль администратора через API
- обновляет `APP_HEALTHCHECK_USERNAME` и `APP_HEALTHCHECK_PASSWORD`
- при флаге `--install-backup-cron` ставит ежедневный backup в `cron`

## Полезные опции

Свой admin-пароль вместо автогенерации:

```bash
./scripts/prepare-release-env.sh --write --rotate-admin-password --admin-password 'YourStrongPassword123'
```

Если текущий пароль админа уже отличается от `APP_HEALTHCHECK_PASSWORD`:

```bash
./scripts/prepare-release-env.sh \
  --write \
  --rotate-admin-password \
  --current-admin-username admin \
  --current-admin-password 'текущий-пароль'
```

Установить backup не в `02:00`, а в `03:30`:

```bash
./scripts/prepare-release-env.sh --write --install-backup-cron --backup-hour 3 --backup-minute 30
```

## Что важно понимать

- после смены `APP_JWT_SECRET` все текущие JWT-сессии после перезапуска станут недействительными
- после смены admin-пароля вход `admin / 00000` больше работать не будет
- новый admin-пароль надо сохранить в безопасном месте
- после применения нужно перезапустить контейнеры

## Финальная проверка

После применения:

```bash
docker compose up -d --build
./scripts/release-check.sh --with-backup
```

Если больше нет `FAIL`, окружение близко к готовности для выкладки.
