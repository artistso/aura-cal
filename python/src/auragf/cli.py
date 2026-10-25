"""Command line entry point: ``agf <command>``.

    agf validate canvas.agf.json      # well-formedness + type diagnostics
    agf show canvas.agf.json          # LaTeX and value for every component
    agf eval canvas.agf.json --root=… --env x=2,y=3
    agf latex canvas.agf.json
    agf sympy canvas.agf.json         # SymPy translation
    agf conformance [corpus dir]      # run the SPEC §9 conformance classes
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .conformance import run
from .evaluate import evaluate
from .latex import to_latex
from .model import Document, load
from .validate import validate


def _parse_env(pairs: list[str]) -> dict[str, float]:
    env: dict[str, float] = {}
    for pair in pairs:
        for item in pair.split(","):
            if not item.strip():
                continue
            name, _, value = item.partition("=")
            if not _:
                raise SystemExit(f"bad --env entry {item!r}; expected name=value")
            env[name.strip()] = float(value)
    return env


def _document(path: str) -> Document:
    document = load(path)
    return document


def cmd_validate(args) -> int:
    document = _document(args.path)
    diagnostics = validate(document)
    for diagnostic in diagnostics:
        print(diagnostic)
    errors = [d for d in diagnostics if d.severity.value == "error"]
    warnings = [d for d in diagnostics if d.severity.value == "warning"]
    print(f"{len(document.nodes)} vertices · {len(document.edges)} edges · "
          f"{len(errors)} errors · {len(warnings)} warnings")
    return 1 if errors else 0


def cmd_show(args) -> int:
    document = _document(args.path)
    for root in sorted(document.roots(), key=lambda n: (n.y, n.x)):
        latex = to_latex(document, root.id)
        value = evaluate(document, root.id)
        suffix = "" if value is None else f"   = {value:.6g}"
        print(f"{latex}{suffix}")
    return 0


def cmd_eval(args) -> int:
    from .evaluate import evaluate_relation

    document = _document(args.path)
    root = args.root or (document.roots()[0].id if document.roots() else None)
    if root is None:
        raise SystemExit("document has no root")
    env = _parse_env(args.env or [])
    value = evaluate(document, root, env)
    if value is not None:
        print(f"{value:.12g}")
        return 0
    # A relation is not a number (SPEC §6); report its truth value instead of "undefined".
    truth = evaluate_relation(document, root, env)
    print("undefined" if truth is None else ("true" if truth else "false"))
    return 0


def cmd_latex(args) -> int:
    document = _document(args.path)
    root = args.root or (document.roots()[0].id if document.roots() else None)
    if root is None:
        raise SystemExit("document has no root")
    print(to_latex(document, root))
    return 0


def cmd_sympy(args) -> int:
    from .sympy_bridge import UnsupportedConstruct, to_sympy

    document = _document(args.path)
    root = args.root or (document.roots()[0].id if document.roots() else None)
    try:
        print(to_sympy(document, root))
    except UnsupportedConstruct as exc:
        print(f"unsupported: {exc}", file=sys.stderr)
        return 2
    return 0


def cmd_conformance(args) -> int:
    corpus = Path(args.corpus) if args.corpus else _default_corpus()
    report = run(corpus)
    print(f"corpus: {corpus}")
    print(report.render())
    print(f"\n{report.passed} passed, {report.failed} failed")
    return 0 if report.failed == 0 else 1


def _default_corpus() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "docs" / "conformance"
        if candidate.exists():
            return candidate
    return Path("docs/conformance")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="agf", description="Aura Graph Format tooling (AGF 0.1)")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("validate", help="report well-formedness diagnostics")
    p.add_argument("path")
    p.set_defaults(func=cmd_validate)

    p = sub.add_parser("show", help="print LaTeX and value for each component")
    p.add_argument("path")
    p.set_defaults(func=cmd_show)

    p = sub.add_parser("eval", help="evaluate the document under an environment")
    p.add_argument("path")
    p.add_argument("--root")
    p.add_argument("--env", action="append", help="x=2,y=3")
    p.set_defaults(func=cmd_eval)

    p = sub.add_parser("latex", help="print the LaTeX projection")
    p.add_argument("path")
    p.add_argument("--root")
    p.set_defaults(func=cmd_latex)

    p = sub.add_parser("sympy", help="translate to a SymPy expression")
    p.add_argument("path")
    p.add_argument("--root")
    p.set_defaults(func=cmd_sympy)

    p = sub.add_parser("conformance", help="run the conformance corpus")
    p.add_argument("corpus", nargs="?")
    p.set_defaults(func=cmd_conformance)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
