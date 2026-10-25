#!/usr/bin/env python3
"""Turn a failed build log into a GitHub annotation.

GitHub's log download endpoint is not reachable from every environment (including, notably,
agent sandboxes), but annotations are served by the regular REST API. Printing the interesting
lines as an ``::error::`` annotation means a red check explains itself where the failure is
reported, instead of only in a log someone has to go and fetch.

Usage (in a workflow step, after capturing the log):

    python3 .github/scripts/annotate_failure.py /tmp/gradle.log "Gradle failed"
"""

from __future__ import annotations

import pathlib
import sys

#: Lines that usually carry the cause, in rough order of usefulness.
MARKERS = (
    "What went wrong",
    "FAILURE:",
    "e: ",          # Kotlin compiler
    "error:",       # javac and most tools
    "Execution failed for task",
    "Caused by:",
    "Could not",
)

MAX_LINES = 25


def interesting(lines: list[str]) -> list[str]:
    hits = [line for line in lines
            if any(marker in line for marker in MARKERS) and not line.startswith("> Task")]
    return hits[:MAX_LINES] or lines[-MAX_LINES:]


def escape(text: str) -> str:
    """Escape per GitHub's workflow-command rules."""
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: annotate_failure.py <logfile> [title]", file=sys.stderr)
        return 2
    path = pathlib.Path(sys.argv[1])
    title = sys.argv[2] if len(sys.argv) > 2 else "Build failed"
    if not path.exists():
        print(f"::error title={title}::no log at {path}")
        return 0
    lines = [line.rstrip() for line in path.read_text(errors="replace").splitlines() if line.strip()]
    print(f"::error title={escape(title)}::{escape(chr(10).join(interesting(lines)))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
