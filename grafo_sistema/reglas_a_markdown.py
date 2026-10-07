#!/usr/bin/env python3
"""Convierte una base de reglas clínicas (JSON) a una tabla Markdown.

Acepta el formato canónico de `base_reglas.json`:

    { "rules": { "ID": { "nombre", "condiciones", "nodoDestino", "nivel",
                         "metadata" } } }

`condiciones` se expresa en forma normal disyuntiva (FND): una lista de casos,
donde cada caso es una lista de condiciones. Se admite también el formato plano
(lista de condiciones) como un único caso.

Solo usa la librería estándar. Salida por defecto en ./generado/.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

BASE = Path(__file__).resolve().parent


def celda(texto) -> str:
    """Escapa un valor para que no rompa la tabla Markdown."""
    return str(texto).replace("|", "\\|").replace("\n", " ").strip()


def formatear_condiciones(condiciones) -> str:
    if not condiciones:
        return "—"
    # Formato plano: lista de condiciones -> un único caso.
    if isinstance(condiciones, list) and condiciones and isinstance(condiciones[0], dict):
        condiciones = [condiciones]

    casos = []
    for caso in condiciones:
        if not isinstance(caso, list):
            caso = [caso]
        terminos = []
        for cond in caso:
            id_nodo = cond.get("idNodo", "?")
            respuesta = cond.get("respuesta", "?")
            terminos.append(f"`{id_nodo}` = `{respuesta}`")
        casos.append(" ∧ ".join(terminos))
    return " ∨<br>".join(casos)


def reglas_a_markdown(documento: dict) -> str:
    reglas = documento.get("rules", {})
    lineas = []

    grafo_id = documento.get("grafoId")
    if grafo_id:
        lineas.append(f"# Base de reglas: {grafo_id}")
        lineas.append("")

    lineas.append("| Clave | Nombre | Condiciones (FND) | Nodo destino | Nivel | Descripción |")
    lineas.append("| --- | --- | --- | --- | --- | --- |")

    conteo: dict[str, int] = {}
    for clave in sorted(reglas):
        regla = reglas[clave] or {}
        metadata = regla.get("metadata") or {}
        nivel = regla.get("nivel", "")
        conteo[nivel] = conteo.get(nivel, 0) + 1
        lineas.append(
            "| {clave} | {nombre} | {condiciones} | `{destino}` | {nivel} | {desc} |".format(
                clave=f"`{clave}`",
                nombre=celda(regla.get("nombre", "")),
                condiciones=formatear_condiciones(regla.get("condiciones")),
                destino=regla.get("nodoDestino", ""),
                nivel=celda(nivel),
                desc=celda(metadata.get("descripcion", "")),
            )
        )

    lineas.append("")
    lineas.append("## Resumen por nivel")
    lineas.append("")
    lineas.append("| Nivel | Reglas |")
    lineas.append("| --- | --- |")
    for nivel in sorted(conteo):
        lineas.append(f"| {celda(nivel)} | {conteo[nivel]} |")
    lineas.append(f"| **Total** | **{len(reglas)}** |")

    return "\n".join(lineas) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "input", nargs="?", type=Path, default=BASE / "base_reglas.json",
        help="JSON de la base de reglas (por defecto: base_reglas.json)",
    )
    parser.add_argument(
        "-o", "--output", type=Path, default=None,
        help="Archivo .md de salida (por defecto: ./generado/<input>.md)",
    )
    args = parser.parse_args()

    with args.input.open(encoding="utf-8") as fh:
        documento = json.load(fh)

    salida = args.output or BASE / "generado" / f"{args.input.stem}.md"
    salida.parent.mkdir(parents=True, exist_ok=True)
    salida.write_text(reglas_a_markdown(documento), encoding="utf-8")
    print(f"escrito {salida}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
