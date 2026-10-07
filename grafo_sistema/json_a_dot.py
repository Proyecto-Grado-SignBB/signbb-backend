#!/usr/bin/env python3
"""Convierte los grafos clínicos en JSON a archivos Graphviz (.dot).

El objetivo es verificar que la estructura definida en el JSON corresponda con
los .dot dibujados a mano. El id de cada nodo del JSON se usa tal cual como id
DOT para que los desajustes (nodos o aristas que no coinciden) queden visibles.

Solo usa la librería estándar. Salidas por defecto en ./generado/.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent

CONFIG_ESPACIADA = """graph [ranksep=1.5, nodesep=1.2];
node [fontname="Helvetica", style=filled, fillcolor=white, margin=0.2];
edge [fontname="Helvetica", fontsize=11, minlen=2];"""

CONFIG_SIMPLE = """node [fontname="Helvetica", style=filled, fillcolor=white];
edge [fontname="Helvetica", fontsize=10];"""

CONFIG_POR_GRAFO = {
    "grafo_raiz": CONFIG_SIMPLE,
    "grafo_respiracion": CONFIG_SIMPLE,
    "grafo_gastro": CONFIG_ESPACIADA,
    "grafo_activo_color": CONFIG_ESPACIADA,
}

ID_VALIDO = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def id_dot(valor: str) -> str:
    """Cita un id solo cuando no es un identificador DOT válido."""
    return valor if ID_VALIDO.match(valor) else '"' + escapar(valor) + '"'


def escapar(texto: str) -> str:
    """Escapa un texto para incrustarlo entre comillas dentro de un label."""
    return texto.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def estilo_nodo(meta: dict) -> str:
    tipo = meta.get("tipo")
    if tipo == "pregunta":
        if meta.get("tipoInteraccion") == "SELECCION_MULTIPLE":
            return "color=blue, fontcolor=blue"
        return "color=black"
    if tipo == "referencia":
        return "color=purple, fontcolor=purple"
    if tipo == "terminal":
        desenlace = meta.get("desenlace")
        if desenlace == "urgencia":
            return "shape=doublecircle, color=red, fontcolor=red"
        if desenlace == "consulta_medica":
            return "shape=doublecircle, color=yellow, fontcolor=darkgoldenrod"
        return "shape=doublecircle, color=green, fontcolor=darkgreen"
    return ""


def nodo_a_dot(nid: str, nodo: dict) -> str:
    label = nodo.get("label", nid)
    attrs = [f'label="{escapar(label)}"']
    extra = estilo_nodo(nodo.get("metadata", {}) or {})
    if extra:
        attrs.append(extra)
    return f"{id_dot(nid)} [{', '.join(attrs)}];"


def arista_a_dot(arista: dict) -> str:
    source = id_dot(arista["source"])
    target = id_dot(arista["target"])
    relacion = arista.get("relation", "")
    return f'{source} -> {target} [label="\\n   {escapar(relacion)}   \\n"];'


def grafo_a_dot(documento: dict, origen: Path) -> str:
    grafo = documento["graph"]
    gid = grafo.get("id", origen.stem)
    config = CONFIG_POR_GRAFO.get(gid, CONFIG_ESPACIADA)

    lineas = [f"digraph {id_dot(gid)} {{"]
    lineas.append(f"// Generado desde {origen.name} — {grafo.get('label', '')}")
    lineas.append("// Configuración general")
    lineas.extend(config.splitlines())
    lineas.append("")

    lineas.append("// NODOS")
    for nid, nodo in grafo.get("nodes", {}).items():
        lineas.append(nodo_a_dot(nid, nodo))
    lineas.append("")

    lineas.append("// ARISTAS")
    for arista in grafo.get("edges", []):
        lineas.append(arista_a_dot(arista))

    lineas.append("}")
    return "\n".join(lineas) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "inputs",
        nargs="*",
        type=Path,
        help="JSON de grafos (por defecto: grafo_*.json junto a este script)",
    )
    parser.add_argument(
        "-o", "--out-dir", type=Path, default=BASE / "generado",
        help="Directorio de salida (por defecto: ./generado)",
    )
    parser.add_argument(
        "--render", action="store_true",
        help="Genera también el .svg con `dot -Tsvg` para validar que compila",
    )
    args = parser.parse_args()

    entradas = args.inputs or sorted(BASE.glob("grafo_*.json"))
    if not entradas:
        print("No se encontraron grafos de entrada.", file=sys.stderr)
        return 1

    args.out_dir.mkdir(parents=True, exist_ok=True)

    for origen in entradas:
        with origen.open(encoding="utf-8") as fh:
            documento = json.load(fh)
        contenido = grafo_a_dot(documento, origen)
        destino = args.out_dir / f"{origen.stem}.dot"
        destino.write_text(contenido, encoding="utf-8")
        print(f"escrito {destino}")

        if args.render:
            subprocess.run(
                ["dot", "-Tsvg", str(destino), "-o", str(destino.with_suffix(".svg"))],
                check=True,
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
