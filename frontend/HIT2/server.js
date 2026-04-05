const express = require('express');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 3001;

const BACKEND_URL = process.env.BACKEND_URL || 'http://host.docker.internal:8080';

app.use(express.json());

// Servir archivos estáticos desde el directorio actual
app.use(express.static(__dirname));

// Proxy para HIT2 - Endpoint principal
app.post('/api/hit2/getRemoteTask', async (req, res) => {
  try {
    const response = await fetch(`${BACKEND_URL}/api/hit2/getRemoteTask`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body)
    });

    const data = await response.json();
    res.status(response.status).json(data);
  } catch (error) {
    res.status(500).json({
      error: 'Error al conectar con el backend HIT2',
      message: error.message
    });
  }
});

// Proxy para HIT2 - Status del pool
app.get('/api/hit2/status', async (req, res) => {
  try {
    const response = await fetch(`${BACKEND_URL}/api/hit2/status`);
    const data = await response.json();
    res.status(response.status).json(data);
  } catch (error) {
    res.status(500).json({
      error: 'Error al obtener status del HIT2',
      message: error.message
    });
  }
});

// Para todas las rutas, devolver el index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`HIT2 Frontend corriendo en http://localhost:${PORT}`);
  console.log(`Backend proxy apuntando a: ${BACKEND_URL}`);
});
