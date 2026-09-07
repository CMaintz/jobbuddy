FROM node:20-alpine AS build
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

FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
