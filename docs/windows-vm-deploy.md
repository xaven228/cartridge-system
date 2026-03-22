# Deploy on Windows VM

## 1. Prerequisites

- Windows VM with internet access
- Docker Desktop installed
- Docker Desktop set to **Linux containers**
- Open inbound port `8080` in Windows Firewall (or your `APP_PORT`)
- Open inbound port `3000` in Windows Firewall (or your `FRONTEND_PORT`)

## 2. Project setup

Open PowerShell in project folder:

```powershell
Copy-Item .env.example .env
.\scripts\preflight.ps1
```

Optional: edit `.env` and change ports/passwords.

## 3. Start services

```powershell
.\scripts\dev-up.ps1
```

This starts:
- `cartridge_postgres`
- `cartridge_backend`
- `cartridge_frontend`

## 4. Check service

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:3000
Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/departments
```

If ports were changed in `.env`, use `FRONTEND_PORT` and `APP_PORT`.

## 5. Stop services

```powershell
.\scripts\dev-down.ps1
```

## 6. Useful commands

```powershell
docker compose ps
docker compose logs -f frontend
docker compose logs -f backend
docker compose logs -f postgres
.\scripts\check-health.ps1
.\scripts\status.ps1
```

## 7. Backup / Restore

Backup:

```powershell
.\scripts\backup-db.ps1
```

Restore:

```powershell
.\scripts\restore-db.ps1 -BackupFile .\backups\<backup-file>.sql
```

Автоматический ежедневный backup (Task Scheduler):
1. Откройте **Task Scheduler** -> **Create Basic Task**
2. Trigger: Daily (например, `02:00`)
3. Action: Start a program
4. Program/script:
   `powershell.exe`
5. Arguments:
   `-ExecutionPolicy Bypass -File "C:\path\to\cartridge-system\scripts\backup-db.ps1"`
6. Finish

Retention:
- Количество дней хранения задаётся в `.env` через `BACKUP_RETENTION_DAYS` (по умолчанию `14`)

## 8. Safe update

Обновление приложения с авто-проверкой и rollback:

```powershell
.\scripts\update.ps1
```

Ручная API-проверка:

```powershell
.\scripts\smoke-test.ps1
```
