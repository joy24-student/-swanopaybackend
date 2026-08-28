FROM node:22-alpine AS dependencies
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev --ignore-scripts

FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
RUN addgroup -S swapnopay && adduser -S -G swapnopay swapnopay
COPY --from=dependencies /app/node_modules ./node_modules
COPY --chown=swapnopay:swapnopay package.json ./
COPY --chown=swapnopay:swapnopay src ./src
COPY --chown=swapnopay:swapnopay sql ./sql
COPY --chown=swapnopay:swapnopay scripts ./scripts
USER swapnopay
EXPOSE 8080
CMD ["node", "src/index.js"]
