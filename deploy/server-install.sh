#!/usr/bin/env bash
set -euo pipefail

APP_NAME="chengdu-neusoft-ai-lesson-plan-system"
APP_DIR="/opt/${APP_NAME}"
SERVICE_NAME="nsu-maic-backend"
DB_NAME="${DB_NAME:-nsu_edu_maic}"
DB_USER="${DB_USER:-nsu_maic}"
DB_PASSWORD="${DB_PASSWORD:-}"
AI_API_KEY="${AI_API_KEY:-}"
AI_MODEL_NAME="${AI_MODEL_NAME:-deepseek-v4-flash}"
AI_BASE_URL="${AI_BASE_URL:-https://api.deepseek.com}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "DB_PASSWORD is required. Example: DB_PASSWORD='your-password' AI_API_KEY='sk-...' bash server-install.sh"
  exit 1
fi

if [[ -z "${AI_API_KEY}" ]]; then
  echo "AI_API_KEY is required. Example: DB_PASSWORD='your-password' AI_API_KEY='sk-...' bash server-install.sh"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "java is required but not installed"
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql is required but not installed"
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "nginx is required but not installed"
  exit 1
fi

if ! command -v libreoffice >/dev/null 2>&1; then
  echo "libreoffice is required but not installed"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_JAR="$(find "${BUNDLE_DIR}/backend" -maxdepth 1 -name '*.jar' | head -n 1)"
TEMPLATE_FILE="$(find "${BUNDLE_DIR}/templates" -maxdepth 1 -name '*.docx' | head -n 1)"

if [[ -z "${BACKEND_JAR}" || ! -f "${BACKEND_JAR}" ]]; then
  echo "Backend jar not found in ${BUNDLE_DIR}/backend"
  exit 1
fi

if [[ ! -d "${BUNDLE_DIR}/frontend/dist" ]]; then
  echo "Frontend dist not found in ${BUNDLE_DIR}/frontend/dist"
  exit 1
fi

if [[ -z "${TEMPLATE_FILE}" || ! -f "${TEMPLATE_FILE}" ]]; then
  echo "Default course plan template not found in ${BUNDLE_DIR}/templates"
  exit 1
fi

mkdir -p "${APP_DIR}/backend" "${APP_DIR}/frontend" "${APP_DIR}/data/uploads/tmp" "${APP_DIR}/data/templates" "${APP_DIR}/logs"
cp "${BACKEND_JAR}" "${APP_DIR}/backend/app.jar"
rm -rf "${APP_DIR}/frontend/dist"
cp -r "${BUNDLE_DIR}/frontend/dist" "${APP_DIR}/frontend/dist"
cp "${TEMPLATE_FILE}" "${APP_DIR}/data/templates/$(basename "${TEMPLATE_FILE}")"

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

cat >/etc/nsu-maic-backend.env <<EOF
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
AI_API_KEY=${AI_API_KEY}
AI_MODEL_NAME=${AI_MODEL_NAME}
AI_BASE_URL=${AI_BASE_URL}
UPLOAD_TMP_DIR=${APP_DIR}/data/uploads/tmp
COURSE_PLAN_DEFAULT_TEMPLATE_PATH=${APP_DIR}/data/templates/$(basename "${TEMPLATE_FILE}")
OCR_ENABLED=false
EOF
chmod 600 /etc/nsu-maic-backend.env

cat >/etc/systemd/system/${SERVICE_NAME}.service <<EOF
[Unit]
Description=Chengdu Neusoft AI Lesson Plan Backend
After=network.target mysql.service

[Service]
Type=simple
WorkingDirectory=${APP_DIR}/backend
EnvironmentFile=/etc/nsu-maic-backend.env
ExecStart=/usr/bin/java -jar ${APP_DIR}/backend/app.jar
Restart=always
RestartSec=5
StandardOutput=append:${APP_DIR}/logs/backend.out.log
StandardError=append:${APP_DIR}/logs/backend.err.log

[Install]
WantedBy=multi-user.target
EOF

cat >/etc/nginx/sites-available/nsu-maic <<EOF
server {
    listen 80;
    server_name _;

    client_max_body_size 300m;

    root ${APP_DIR}/frontend/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 1800s;
        proxy_send_timeout 1800s;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

ln -sf /etc/nginx/sites-available/nsu-maic /etc/nginx/sites-enabled/nsu-maic
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl daemon-reload
systemctl enable ${SERVICE_NAME}
systemctl restart ${SERVICE_NAME}
systemctl restart nginx

echo "Deployment completed. Visit: http://8.137.148.233"
