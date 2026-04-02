const express = require('express');
const app = express();
app.use(express.json());

app.post("/ejecutar", (req, res) => {
    //Defino lo que espera recibir cuando se levanta el servicio
    const { calculo, parametros, datosAdicionales } = req.body ?? {};
    try {
        let resultado;

        if (calculo === "sumar") {
            const a = Number(parametros?.a);
            const b = Number(parametros?.b);

            if (Number.isNaN(a) || Number.isNaN(b)) {
                return res.status(400).json({
                    status: "ERROR",
                    resultado: null,
                    mensaje: "Parámetros invalidos para realizar una Suma"
                });
            }
            resultado = a + b;

        } else if (calculo === "multiplicar") {
            const a = Number(parametros?.a);
            const b = Number(parametros?.b);
            if (Number.isNaN(a) || Number.isNaN(b)) {
                return res.status(400).json({
                    status: "ERROR",
                    resultado: null,
                    mensaje: "Parámetros invalidos para realizar una Multiplicación"
                });
            }
            resultado = a * b;

        } else {
            return res.status(400).json({
                status: "ERROR",
                resultado: null,
                mensaje: "Cálculo no soportado"
            });
        }
        return res.status(200).json({
            status: "OK",
            resultado: resultado,
            mensaje: "Tarea ejecutada correctamente",
            datosAdcionalesRecibidos: datosAdicionales ?? null
        });
    } catch (e) {
        return res.status(500).json({ status: "ERROR", resultado: null, mensaje: e.message });
    }
});
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
    console.log(`Task service corriendo en puerto ${PORT}`);

});
