FROM node:18-alpine

RUN apk add --no-cache bash

WORKDIR /app

COPY package*.json ./

RUN npm install --only=production

COPY . .

EXPOSE 3011 3012 3013 3014 3015

CMD ["sh", "-c", "if [ \"$MULTI_CLIENT\" = \"true\" ]; then PORT=3011 node server.js & PORT=3012 node server.js & PORT=3013 node server.js & PORT=3014 node server.js & PORT=3015 node server.js & wait; else node server.js; fi"]
