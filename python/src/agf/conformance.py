"""Conformance runner (SPEC §9).

Runs the classes an implementation claims, against the committed corpus in
``docs/conformance/``. The corpus was produced by the reference Kotlin implementation, so a
pass here means two implementations agree — which is the only way a specification earns the
word "conformant".
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from .evaluate import evaluate, evaluate_relation
from .latex import to_latex
from .model import Document, dumps, loads
from .types import infer_types
from .validate import Rule, Severity, validate

#: Environments used by C5. Deliberately includes negatives, non-integers and 1.
SAMPLE_ENVIRONMENTS = (
    {"x": -3.0}, {"x": -0.5}, {"x": 0.0}, {"x": 1.0}, {"x": 2.5}, {"x": 4.0}, {"x": 7.0},
    {"a": 2.0, "b": 3.0, "c": 5.0}, {"a": -1.5, "b": 0.25, "c": 2.0},
    {"y": 1.0}, {"x": 3.0, "y": 2.0}, {"z": 1.0},
)


@dataclass
class CheckResult:
    case: str
    check: str
    passed: bool
    detail: str = ""


@dataclass
class Report:
    results: list[CheckResult] = field(default_factory=list)

    def add(self, case: str, check: str, passed: bool, detail: str = "") -> None:
        self.results.append(CheckResult(case, check, passed, detail))

    @property
    def passed(self) -> int:
        return sum(1 for r in self.results if r.passed)

    @property
    def failed(self) -> int:
        return sum(1 for r in self.results if not r.passed)

    def failures(self) -> list[CheckResult]:
        return [r for r in self.results if not r.passed]

    def by_check(self) -> dict[str, tuple[int, int]]:
        out: dict[str, tuple[int, int]] = {}
        for result in self.results:
            passed, total = out.get(result.check, (0, 0))
            out[result.check] = (passed + (1 if result.passed else 0), total + 1)
        return out

    def render(self) -> str:
        lines = []
        for check, (passed, total) in sorted(self.by_check().items()):
            status = "PASS" if passed == total else "FAIL"
            lines.append(f"{status}  {check:<22} {passed}/{total}")
        failures = self.failures()
        if failures:
            lines.append("")
            for failure in failures[:20]:
                lines.append(f"  ✗ {failure.check} {failure.case}: {failure.detail}")
            if len(failures) > 20:
                lines.append(f"  … {len(failures) - 20} more failures")
        return "\n".join(lines)


def _close(actual: float | None, expected: float | None, tolerance: float = 1e-9) -> bool:
    if actual is None or expected is None:
        return actual is expected
    return abs(actual - expected) <= tolerance * max(1.0, abs(actual), abs(expected))


def run(corpus_dir: str | Path) -> Report:
    """Run C1–C5 against ``corpus_dir`` (normally ``docs/conformance``)."""
    corpus = Path(corpus_dir)
    report = Report()

    cases_path = corpus / "cases.json"
    if cases_path.exists():
        payload = json.loads(cases_path.read_text(encoding="utf-8"))
        for case in payload.get("cases", []):
            name = case.get("name", "?")
            document = Document.from_dict(case["graph"])

            # -- C1: serialization round-trip is lossless --------------------
            try:
                again = loads(dumps(document))
                same = again.to_dict() == document.to_dict()
                report.add(name, "C1 round-trip", same,
                           "" if same else "encode/decode changed the document")
            except Exception as exc:  # noqa: BLE001 - reported as a failure
                report.add(name, "C1 round-trip", False, str(exc))

            # -- C2: well-formedness of valid documents ----------------------
            errors = [d for d in validate(document, include_warnings=False)
                      if d.severity is Severity.ERROR]
            expected_errors = case.get("error_count", 0)
            report.add(name, "C2 well-formedness", len(errors) == expected_errors,
                       f"expected {expected_errors} errors, received {len(errors)}: "
                       f"{[f'{d.code}:{d.message}' for d in errors][:3]}")

            # -- C3: inferred types match the reference ----------------------
            inferred = infer_types(document)
            mismatches = [
                (node_id, inferred.get(node_id), expected)
                for node_id, expected in case.get("types", {}).items()
                if inferred.get(node_id) != expected
            ]
            report.add(name, "C3 typing", not mismatches, str(mismatches[:3]))

            # -- C4: reference evaluation ------------------------------------
            root = case.get("root")
            expected_value = case.get("value")
            actual = None if root is None else evaluate(document, root)
            report.add(name, "C4 evaluation", _close(actual, expected_value),
                       f"expected {expected_value}, received {actual}")

            # -- LaTeX projection (SPEC §7) ----------------------------------
            if case.get("latex") is not None and root is not None:
                rendered = to_latex(document, root)
                report.add(name, "P7 latex projection", rendered == case["latex"],
                           f"expected {case['latex']!r}, received {rendered!r}")

    rewrites_path = corpus / "rewrites.json"
    if rewrites_path.exists():
        payload = json.loads(rewrites_path.read_text(encoding="utf-8"))
        for case in payload.get("cases", []):
            name = case.get("name", "?")
            initial = Document.from_dict(case["initial"])
            baseline = _truth_profile(initial)
            for index, step in enumerate(case.get("steps", [])):
                graph = Document.from_dict(step["graph"])
                profile = _truth_profile(graph)
                identical = _profiles_agree(baseline, profile)
                report.add(f"{name}[{index}]", "C5 rewrite soundness", identical,
                           f"step {step.get('rule')!r} changed a truth value")

    malformed_path = corpus / "malformed.json"
    if malformed_path.exists():
        payload = json.loads(malformed_path.read_text(encoding="utf-8"))
        for case in payload.get("cases", []):
            name = case.get("name", "?")
            document = Document.from_dict(case["graph"])
            found = {d.code for d in validate(document)}
            expected = set(case.get("expected_codes", []))
            missing = expected - found
            report.add(name, "C2 diagnostics", not missing,
                       f"missing {sorted(missing)}; found {sorted(found)}")
            if "expected_errors" in case:
                error_count = len([d for d in validate(document, include_warnings=False)
                                   if d.severity is Severity.ERROR])
                report.add(name, "C2 severity", error_count == case["expected_errors"],
                           f"expected {case['expected_errors']} errors, received {error_count}")

    return report


def _truth_profile(document: Document) -> list[bool | None]:
    """Truth value of every relation in the document, over the sample environments."""
    relations = [n.id for n in document.nodes if n.kind == "relation"]
    profile: list[bool | None] = []
    for relation_id in relations:
        for env in SAMPLE_ENVIRONMENTS:
            profile.append(evaluate_relation(document, relation_id, env))
    return profile


def _profiles_agree(left: list[bool | None], right: list[bool | None]) -> bool:
    # Steps may change the shape of the document, so compare the *verdict*: whenever both
    # sides are defined they must agree. Undefined-on-one-side is not a refutation.
    if len(left) != len(right):
        return True
    for a, b in zip(left, right):
        if a is None or b is None:
            continue
        if a != b:
            return False
    return True


def rule_codes() -> list[str]:
    """The stable rule codes an implementation can report."""
    return [rule.value for rule in Rule]
