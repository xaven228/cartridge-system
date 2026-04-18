# Cartridge System

Минимальный backend для учёта картриджей (Spring Boot + PostgreSQL + Flyway).

## Frontend (React)

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

В dev-режиме фронт по умолчанию ходит в `http://localhost:8080` (`VITE_API_BASE_URL`).
В Docker-режиме frontend использует относительный `/api` и проксирует запросы через nginx на backend.
Вход выполняется по логину и паролю через backend (`/api/auth/login`).

## Вариант для VM (рекомендуется)

0. Создать локальный env-файл:

```bash
cp .env.example .env
```

1. Проверить готовность VM:

```bash
./scripts/preflight.sh
```

2. Запуск всего окружения в Docker (PostgreSQL + backend + frontend):

```bash
./scripts/dev-up.sh
```

3. Остановка окружения:

```bash
./scripts/dev-down.sh
```

Frontend по умолчанию доступен на `http://localhost:3000` (или `FRONTEND_PORT` из `.env`).
Backend API доступен на `http://localhost:8080` (или `APP_PORT` из `.env`).

## Windows VM (PowerShell)

Требования:
- Установлен Docker Desktop (режим Linux containers)
- Открыт порт `APP_PORT` в Windows Firewall (по умолчанию `8080`)
- Открыт порт `FRONTEND_PORT` в Windows Firewall (по умолчанию `3000`)

```powershell
Copy-Item .env.example .env
.\scripts\dev-up.ps1
# остановка:
.\scripts\dev-down.ps1
```

Подробный гайд: [docs/windows-vm-deploy.md](docs/windows-vm-deploy.md)

## После старта

- Frontend: `http://localhost:3000` (или ваш `FRONTEND_PORT`)
- API: `http://localhost:8080/api/departments` (или ваш `APP_PORT`)
- Логин по умолчанию: `admin`
- Пароль по умолчанию: `password`
- Важно: после первого входа смените пароль и задайте свой `APP_JWT_SECRET` в `.env`
- Логи backend:
```bash
docker compose logs -f backend
```
- Health check:
```bash
./scripts/check-health.sh
```

PowerShell:
```powershell
.\scripts\check-health.ps1
```

Краткий статус окружения:
```bash
./scripts/status.sh
```

PowerShell:
```powershell
.\scripts\status.ps1
```

## Обновление приложения

Linux/macOS:
```bash
./scripts/update.sh
```

Windows PowerShell:
```powershell
.\scripts\update.ps1
```

Поведение:
- пересборка и перезапуск контейнеров,
- автоматический `health-check`,
- автоматический `smoke-test` API,
- если любая проверка падает, выполняется rollback на предыдущий backend image.

Ручной smoke-test:
```bash
./scripts/smoke-test.sh
```

PowerShell:
```powershell
.\scripts\smoke-test.ps1
```

## Бэкап и восстановление БД

Linux/macOS:

```bash
./scripts/backup-db.sh
./scripts/restore-db.sh backups/<backup-file>.sql
```

Windows PowerShell:

```powershell
.\scripts\backup-db.ps1
.\scripts\restore-db.ps1 -BackupFile .\backups\<backup-file>.sql
```

Ротация бэкапов:
- Управляется `BACKUP_RETENTION_DAYS` в `.env` (по умолчанию `14`)
- Старые `cartridge_db-*.sql` удаляются автоматически при каждом запуске backup-скрипта


## Развитие архитектуры

В проекте добавлен модульный каркас для роста функционала (инвентаризация, заявки по залам).

Подробности: [docs/architecture-roadmap.md](docs/architecture-roadmap.md)

## Полный сброс БД

Если нужно убрать вообще все данные и поднять пустую базу без демо-записей:

Linux/macOS:

```bash
./scripts/reset-db.sh
```

Windows PowerShell:

```powershell
.\scripts\reset-db.ps1
```

Команда удаляет Docker volume PostgreSQL и поднимает базу заново.

## Ручной запуск без Docker (опционально)

1. Поднять PostgreSQL:

```bash
docker compose up -d postgres
```

2. Запустить backend:

```bash
cd backend
mvn spring-boot:run
```

## Если порт 8080 занят

```bash
cd backend
SERVER_PORT=8081 mvn spring-boot:run
```

## Конфигурация

Основные параметры лежат в `.env`:

- Docker mode:
  - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
  - `POSTGRES_HOST_PORT` (порт Postgres на VM)
  - `APP_PORT` (порт backend на VM)
  - `FRONTEND_PORT` (порт frontend на VM)
  - `APP_JWT_SECRET` (секрет подписи JWT-токенов)
  - `APP_JWT_TTL_MINUTES` (время жизни токена, по умолчанию `30`)
  - `APP_HALL_REQUEST_SLA_LOW_HOURS`, `APP_HALL_REQUEST_SLA_MEDIUM_HOURS`, `APP_HALL_REQUEST_SLA_HIGH_HOURS`, `APP_HALL_REQUEST_SLA_URGENT_HOURS` (SLA для заявок по залам в часах)
  - `APP_HALL_REQUEST_ESCALATION_INTERVAL_MS` (как часто авто-эскалация проверяет просроченные заявки)
- Manual mode (без Docker backend):
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SERVER_PORT`

## Тесты

```bash
cd backend
mvn test
```
