#!/usr/bin/env bash
#
# Regenera los SVG de los grafos dibujados a mano y las tablas Markdown de
# reglas a partir de los JSON de docs/entorno-pruebas/datos/.
#
# Requisitos: graphviz (`dot`) y python3.
#
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="$(cd "$AQUI/.." && pwd)"
cd "$BASE"

if ! command -v dot >/dev/null 2>&1; then
  echo "Falta graphviz: instala el comando 'dot'." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "Falta python3." >&2
  exit 1
fi

echo "== Regenerando SVG desde los .dot =="
for archivo in grafos/*.dot; do
  dot -Tsvg "$archivo" -o "${archivo%.dot}.svg"
  echo "  ${archivo%.dot}.svg"
done

echo "== Validando JSON de grafos y reglas =="
for archivo in datos/*.json; do
  python3 -m json.tool "$archivo" >/dev/null
  echo "  $archivo"
done

echo "== Generando DOT/SVG desde los JSON (generado/) =="
python3 scripts/json_a_dot.py datos/grafo_*.json -o generado --render

echo "== Generando tablas Markdown de reglas =="
for archivo in datos/reglas_*.json; do
  nombre="$(basename "$archivo" .json)"
  python3 scripts/reglas_a_markdown.py "$archivo" -o "reglas/${nombre}.md"
done
python3 scripts/reglas_a_markdown.py datos/base_reglas.json -o reglas/base_reglas.md

echo "Listo. Revisa grafos/*.svg y reglas/*.md."
