# Развёртывание на Debian через Docker

## Назначение

Этот сценарий подходит для целевого Linux-сервера на Debian, где приложение должно подниматься через Docker Compose.

Стек:
- `postgres:16`
- backend: Spring Boot 3 + Kotlin + Flyway
- frontend: React + nginx

## 1. Требования

Нужно подготовить:
- Debian 12 или совместимый сервер
- пользователь с правами `sudo`
- установленный `git`
- открытые порты для frontend и backend
- доступ в интернет для первой сборки Docker-образов

Минимально нужны порты:
- `3000` для frontend
- `8080` для backend
- `5433` для доступа к PostgreSQL с хоста, если он нужен снаружи

Если внешний доступ к БД не требуется, порт PostgreSQL лучше ограничить firewall-правилами.
В текущей конфигурации проекта backend и PostgreSQL уже по умолчанию привязаны только к `127.0.0.1`, а наружу публикуется frontend.

## 2. Установка Docker

Пример для Debian:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

После добавления пользователя в группу `docker` нужно перелогиниться.

## 3. Подготовка проекта

```bash
git clone <repo-url> cartridge-system
cd cartridge-system
cp .env.example .env
```

Минимум, что нужно изменить в `.env` перед продом:
- `POSTGRES_PASSWORD`
- `APP_JWT_SECRET`
- при необходимости `APP_PORT` и `FRONTEND_PORT`

Параметры сетевой публикации:
- `FRONTEND_HOST_BIND=0.0.0.0`
- `APP_HOST_BIND=127.0.0.1`
- `POSTGRES_HOST_BIND=127.0.0.1`

Это безопасная схема по умолчанию: приложение доступно снаружи через frontend, а backend и база остаются доступны только локально на сервере.

Если на сервере уже заняты `3000` или `8080`, поменяй их в `.env` до первого запуска.

## 4. Первый запуск

```bash
./scripts/preflight.sh
docker compose up -d --build
```

После запуска:
- frontend: `http://<server-ip>:3000`
- backend: `http://<server-ip>:8080`

Проверка:

```bash
./scripts/check-health.sh
./scripts/status.sh
```

## 5. Дефолтный вход

По умолчанию:
- логин: `admin`
- пароль: `00000`

Если пароль нужно вернуть к дефолтному:

```bash
./scripts/reset-admin-password.sh
```

После первого входа пароль администратора лучше изменить.

## 6. Повседневное управление

Запуск:

```bash
docker compose up -d
```

Остановка:

```bash
docker compose down
```

Просмотр контейнеров:

```bash
docker compose ps
```

Логи:

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

## 7. Обновление приложения

Для штатного обновления есть готовый скрипт:

```bash
./scripts/update.sh
```

Что он делает:
- пересобирает и поднимает контейнеры
- запускает health-check
- запускает smoke-test
- пытается откатиться на предыдущий backend/frontend image при неуспехе

## 8. Резервное копирование

Ручной backup:

```bash
./scripts/backup-db.sh
```

Ручное восстановление:

```bash
./scripts/restore-db.sh backups/<backup-file>.sql
```

Автоматическая ежедневная установка в cron:

```bash
./scripts/install-backup-cron.sh
```

По умолчанию backup ставится на `02:00`.

Если нужно другое время:

```bash
./scripts/install-backup-cron.sh 3 30
```

Это создаст ежедневный запуск в `03:30`.

## 9. Firewall

Если используется `ufw`, типовой набор:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 3000/tcp
sudo ufw allow 8080/tcp
sudo ufw enable
```

Если backend не должен быть доступен снаружи, не открывай `8080` и публикуй только frontend.

Для текущей конфигурации именно так и сделано по умолчанию.

## 10. Что проверить перед сдачей

Минимальный чек-лист:
- `docker compose ps` показывает `healthy`/`running`
- `http://<server-ip>:3000` открывается
- вход под `admin` работает
- `./scripts/check-health.sh` завершается без ошибок
- `./scripts/backup-db.sh` создаёт файл в `backups/`
- `./scripts/update.sh` выполняется без падений

Подготовка секрета и cron перед сдачей:

```bash
./scripts/prepare-release-env.sh --write --rotate-admin-password --install-backup-cron
docker compose up -d --build
./scripts/release-check.sh --with-backup
```
