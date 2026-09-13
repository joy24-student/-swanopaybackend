FROM composer:2 AS dependencies
WORKDIR /dependencies
COPY shop/composer.json shop/composer.lock ./
RUN composer install --no-dev --prefer-dist --no-interaction --optimize-autoloader --ignore-platform-reqs

FROM node:22-alpine
WORKDIR /app
COPY swapnopay-backend/package.json swapnopay-backend/package-lock.json ./
RUN npm ci --omit=dev
COPY swapnopay-backend/src ./src
COPY swapnopay-backend/sql ./sql
COPY --from=dependencies /dependencies/vendor /opt/store-vendor
RUN addgroup -g 10001 shop-runtime && addgroup node shop-runtime && mkdir -p uploads/kyc && chown -R node:node /app
USER node
EXPOSE 4000
CMD ["node", "src/index.js"]
