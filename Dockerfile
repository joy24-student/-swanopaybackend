FROM node:20-alpine

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY src ./src
RUN mkdir -p uploads/kyc && chown -R node:node /app

EXPOSE 4000

USER node
CMD ["node", "src/index.js"]
