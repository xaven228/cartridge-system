# Deploy on Windows VM

## 1. Prerequisites

- Windows VM with internet access
- Docker Desktop installed
- Docker Desktop set to **Linux containers**
- Open inbound port `8080` in Windows Firewall (or your `APP_PORT`)

## 2. Project setup

Open PowerShell in project folder:

```powershell
Copy-Item .env.example .env
```

Optional: edit `.env` and change ports/passwords.

## 3. Start services

```powershell
.\scripts\dev-up.ps1
```

This starts:
- `cartridge_postgres`
- `cartridge_backend`

## 4. Check service

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/departments
```

If `APP_PORT` was changed in `.env`, use that port.

## 5. Stop services

```powershell
.\scripts\dev-down.ps1
```

## 6. Useful commands

```powershell
docker compose ps
docker compose logs -f backend
docker compose logs -f postgres
.\scripts\check-health.ps1
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
