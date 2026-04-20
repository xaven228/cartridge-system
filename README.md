# Cartridge System

Минимальный backend для учёта картриджей (Spring Boot + PostgreSQL + Flyway).

## Этап по ТЗ

Сейчас идёт финальная приёмочная шлифовка по ТЗ.

Уже закрыто в текущей версии:
- авторизация по логину и паролю
- роли и доступ в админ-панель
- индивидуально настраиваемые права пользователей
- учёт подразделений, принтеров, картриджей и заправок
- поиск, фильтры и пагинация в основных, админских, сервисных и отчётных списках
- статусы отделов и кабинетов с мягким выводом из использования вместо физического удаления
- запрет на создание кабинетов в отделах, выведенных из использования
- совместимость моделей картриджей с моделями принтеров
- проверка совместимости прямо при создании и редактировании принтера
- аудит действий, входов и изменений
- отчёты на экране, в Excel и PDF
- уведомления по настраиваемым порогам
- авто-завершение сессии через 30 минут неактивности без разлогинивания при активной работе
- собственные модальные подтверждения для чувствительных действий
- backend полностью переведён на Kotlin
- архитектурный каркас оставлен расширяемым под следующие модули: инвентаризация и заявки по залам

Текущее окружение для разработки:
- Linux desktop: Fedora
- целевой сервер: Debian
- запуск: Docker Compose
- по умолчанию наружу открыт только frontend, backend и PostgreSQL привязаны к `127.0.0.1`

Быстрый старт:

```bash
cp .env.example .env
docker compose up -d --build
```

После старта:
- frontend: `http://localhost:3000`
- backend: `http://localhost:8080`
- логин по умолчанию: `admin`
- пароль по умолчанию: `00000`

Документация:
- [Руководство пользователя](docs/user-guide.md)
- [Руководство администратора](docs/admin-guide.md)
- [Развёртывание на Debian через Docker](docs/debian-docker-deploy.md)
- [Предрелизный checklist](docs/release-checklist.md)
- [Подготовка продового окружения](docs/production-hardening.md)
- [Архитектура системы](docs/system-architecture.md)
- [Архитектура развития системы](docs/architecture-roadmap.md)

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
- Пароль по умолчанию: `00000`
- Важно: после первого входа смените пароль и задайте свой `APP_JWT_SECRET` в `.env`

Если база была поднята раньше и пароль `admin` уже меняли (миграция `V18__seed_admin_user.sql` не перезаписывает пароль из-за `ON CONFLICT DO NOTHING`), можно сбросить пароль к дефолтному:

```bash
./scripts/reset-admin-password.sh
```
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

Предрелизная проверка:
```bash
./scripts/release-check.sh
```

Подготовка продовых секретов:
```bash
./scripts/prepare-release-env.sh --write --rotate-admin-password --install-backup-cron
docker compose up -d --build
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

Автоматическая ежедневная установка backup в `cron`:

```bash
./scripts/install-backup-cron.sh
```


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
  - `POSTGRES_HOST_BIND` (по умолчанию `127.0.0.1`)
  - `POSTGRES_HOST_PORT` (порт Postgres на VM)
  - `APP_HOST_BIND` (по умолчанию `127.0.0.1`)
  - `APP_PORT` (порт backend на VM)
  - `FRONTEND_HOST_BIND` (по умолчанию `0.0.0.0`)
  - `FRONTEND_PORT` (порт frontend на VM)
  - `APP_JWT_SECRET` (секрет подписи JWT-токенов)
  - `APP_JWT_TTL_MINUTES` (время жизни токена, по умолчанию `30`)
  - `APP_HEALTHCHECK_USERNAME`, `APP_HEALTHCHECK_PASSWORD` (пользователь для `check-health`, `smoke-test`, `release-check`)
  - `APP_HALL_REQUEST_SLA_LOW_HOURS`, `APP_HALL_REQUEST_SLA_MEDIUM_HOURS`, `APP_HALL_REQUEST_SLA_HIGH_HOURS`, `APP_HALL_REQUEST_SLA_URGENT_HOURS` (SLA для заявок по залам в часах)
  - `APP_HALL_REQUEST_ESCALATION_INTERVAL_MS` (как часто авто-эскалация проверяет просроченные заявки)
- Manual mode (без Docker backend):
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SERVER_PORT`

## Тесты

```bash
cd backend
mvn test
```
