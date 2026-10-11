FROM node:22.23.3-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --quiet
COPY . .

# Firebase web config — injected at build time from .env so the committed
# environment.prod.ts stays a placeholder and never contains real credentials.
ARG FIREBASE_API_KEY
ARG FIREBASE_AUTH_DOMAIN
ARG FIREBASE_PROJECT_ID
ARG FIREBASE_STORAGE_BUCKET
ARG FIREBASE_MESSAGING_SENDER_ID
ARG FIREBASE_APP_ID

RUN sed -i \
    -e "s|REPLACE_WITH_FIREBASE_WEB_API_KEY|${FIREBASE_API_KEY}|g" \
    -e "s|REPLACE_WITH_FIREBASE_AUTH_DOMAIN|${FIREBASE_AUTH_DOMAIN}|g" \
    -e "s|REPLACE_WITH_FIREBASE_PROJECT_ID|${FIREBASE_PROJECT_ID}|g" \
    -e "s|REPLACE_WITH_FIREBASE_STORAGE_BUCKET|${FIREBASE_STORAGE_BUCKET}|g" \
    -e "s|REPLACE_WITH_FIREBASE_MESSAGING_SENDER_ID|${FIREBASE_MESSAGING_SENDER_ID}|g" \
    -e "s|REPLACE_WITH_FIREBASE_APP_ID|${FIREBASE_APP_ID}|g" \
    src/environments/environment.prod.ts

RUN npm run build -- --configuration production

# Unprivileged nginx: runs as uid 101 and listens on 8080, not root on 80.
FROM nginxinc/nginx-unprivileged:1.31.6-alpine@sha256:b9241c6e7b8e9a862f129d8d4199ab64b10390949a78bdd5603379b32c844083
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1
