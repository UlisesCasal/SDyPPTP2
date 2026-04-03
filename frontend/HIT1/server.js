const express = require('express');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 3000;

const BACKEND_URL = process.env.BACKEND_URL || 'http://host.docker.internal:8080';

app.use(express.json());

// Servir archivos estáticos desde el directorio actual
app.use(express.static(__dirname));

// Proxy para las peticiones al backend
app.post('/api/hit1/getRemoteTask', async (req, res) => {
  try {
    const response = await fetch(`${BACKEND_URL}/api/hit1/getRemoteTask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(req.body)
    });

    const data = await response.json();
    res.status(response.status).json(data);
  } catch (error) {
    res.status(500).json({
      error: 'Error al conectar con el backend',
      message: error.message
    });
  }
});

// Para todas las rutas, devolver el index.html (útil para SPA)
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Servidor frontend corriendo en http://localhost:${PORT}`);
  console.log(`Backend proxy apuntando a: ${BACKEND_URL}`);
});