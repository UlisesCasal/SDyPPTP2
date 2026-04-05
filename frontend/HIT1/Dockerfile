# Usar la imagen oficial de Node.js
FROM node:18-alpine

# Instalar bash para el script de múltiples instancias
RUN apk add --no-cache bash

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar los archivos de package y package-lock
COPY package*.json ./

# Instalar dependencias
RUN npm install --only=production

# Copiar el resto de los archivos
COPY . .

# Exponer los puertos para los 5 clientes
EXPOSE 3001 3002 3003 3004 3005

# Comando para iniciar la aplicación
# Por defecto corre 1 instancia, usar CMD_OVERRIDE para correr los 5
CMD ["sh", "-c", "if [ \"$MULTI_CLIENT\" = \"true\" ]; then PORT=3001 node server.js & PORT=3002 node server.js & PORT=3003 node server.js & PORT=3004 node server.js & PORT=3005 node server.js & wait; else node server.js; fi"]