FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src
COPY sql ./sql
RUN addgroup -g 10001 shop-runtime && addgroup node shop-runtime
RUN mkdir -p uploads/kyc && chown -R node:node /app

EXPOSE 4000

USER node
CMD ["node", "src/index.js"]
