# Cartridge System

Минимальный backend для учёта картриджей (Spring Boot + PostgreSQL + Flyway).

## Вариант для VM (рекомендуется)

0. Создать локальный env-файл:

```bash
cp .env.example .env
```

1. Запуск всего окружения в Docker (PostgreSQL + backend):

```bash
./scripts/dev-up.sh
```

2. Остановка окружения:

```bash
./scripts/dev-down.sh
```

Приложение по умолчанию доступно на `http://localhost:8080` (или `APP_PORT` из `.env`).

## Windows VM (PowerShell)

Требования:
- Установлен Docker Desktop (режим Linux containers)
- Открыт порт `APP_PORT` в Windows Firewall (по умолчанию `8080`)

```powershell
Copy-Item .env.example .env
.\scripts\dev-up.ps1
# остановка:
.\scripts\dev-down.ps1
```

Подробный гайд: [docs/windows-vm-deploy.md](docs/windows-vm-deploy.md)

## После старта

- API: `http://localhost:8080/api/departments` (или ваш `APP_PORT`)
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
- Manual mode (без Docker backend):
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SERVER_PORT`

## Тесты

```bash
cd backend
mvn test
```
