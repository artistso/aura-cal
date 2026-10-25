"""Shared fixtures: locate the conformance corpus regardless of the working directory."""

from pathlib import Path

import pytest


@pytest.fixture(scope="session")
def corpus_dir() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "docs" / "conformance"
        if candidate.exists():
            return candidate
    pytest.skip("docs/conformance not found")


@pytest.fixture(scope="session")
def cases(corpus_dir: Path):
    import json

    payload = json.loads((corpus_dir / "cases.json").read_text(encoding="utf-8"))
    return {case["name"]: case for case in payload["cases"]}
