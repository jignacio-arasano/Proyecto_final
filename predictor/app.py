from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
import os
from sklearn.linear_model import LinearRegression

app = Flask(__name__)
CORS(app)


def predict_demand(sales_history):
    """
    Calcula cuánto se va a vender el mes que viene mirando el histórico de ventas.
    Los meses van por orden de carga (el 1er ZIP es el mes 1, el 2do el mes 2, etc).

    Según cuántos meses tengo, hago una cosa distinta:
    - 0 meses : no devuelvo nada (None), no hay con qué arrancar.
    - 1 mes   : repito esa misma cantidad (no tengo cómo saber si sube o baja).
    - 2 meses : promedio dándole más peso al mes más nuevo. Con solo 2 datos
                no se puede confiar en una "tendencia" (dos puntos siempre dan
                una recta perfecta), así que estirar esa recta es peligroso: si
                cayó fuerte proyecta demanda negativa (que termina en 0, irreal)
                y si subió fuerte se va para arriba. El promedio es más prudente.
    - 3+ meses: regresión lineal estirada al mes siguiente, pero con un piso
                para que una caída no me lleve la predicción a 0 de una.

    sales_history: lista de {"period": ..., "quantity": N}
    """
    n = len(sales_history)
    if n == 0:
        return None

    # Armo una lista solo con las cantidades vendidas de cada mes.
    quantities = []
    for s in sales_history:
        quantities.append(float(s["quantity"]))

    if n == 1:
        return max(0, round(quantities[0]))

    if n == 2:
        # El mes más nuevo pesa el doble que el anterior.
        older = quantities[0]
        recent = quantities[1]
        weighted = (older * 1 + recent * 2) / 3
        return max(0, round(weighted))

    # 3 meses o más: uso regresión lineal con un piso de seguridad.
    X = np.arange(n).reshape(-1, 1)
    y = np.array(quantities)

    model = LinearRegression()
    model.fit(X, y)

    next_idx = np.array([[n]])
    predicted = model.predict(next_idx)[0]

    # Piso: aunque venga cayendo, no me parece prudente suponer que la demanda
    # baja más del 50% respecto del último mes que tengo.
    floor = 0.5 * quantities[-1]
    predicted = max(predicted, floor)

    return max(0, round(predicted))


@app.route("/predict", methods=["POST"])
def predict():
    data = request.get_json()
    if not data:
        return jsonify({"error": "No data provided"}), 400

    product_sku = data.get("product_sku", "unknown")
    sales_history = data.get("sales_history", [])
    current_stock = int(data.get("current_stock", 0))
    restock_threshold = int(data.get("restock_threshold", 5))

    if len(sales_history) == 0:
        return jsonify({
            "product_sku": product_sku,
            "predicted_demand": None,
            "alert": False,
            "message": "Sin datos de ventas para este producto"
        })

    predicted = predict_demand(sales_history)
    months_of_data = len(sales_history)
    alert = predicted is not None and current_stock < max(predicted, restock_threshold)

    return jsonify({
        "product_sku": product_sku,
        "predicted_demand": predicted,
        "alert": alert,
        "current_stock": current_stock,
        "restock_threshold": restock_threshold,
        "months_of_data": months_of_data
    })


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    app.run(host="0.0.0.0", port=port, debug=False)
