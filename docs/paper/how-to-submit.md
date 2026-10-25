# Submitting the report

The report (`aura-agf.md`) is written to be submittable as an arXiv technical report. This is a
checklist, not advice on whether to submit — read §9 of the report first, because the report itself
states that AGF has no third-party implementation and no external adoption. That is normal for a
first report; it is a problem only if the abstract reads as if it were otherwise.

## 1. Before you submit

- [ ] **Put your name on it.** A format with no identifiable author reads as abandoned. `artistso`
      works, a legal name works better for citation.
- [ ] Make the code citable *at this commit*: GitHub → *Releases* → tag `agf-0.1` (or `v2.1`).
      arXiv readers follow a link to a moving default branch and then cannot reproduce anything.
- [ ] Archive the release on Zenodo (free), which mints a DOI. Put the DOI in the report and in
      `docs/CITATION.cff` (`identifiers:` block).
- [ ] Proofread the claims against `docs/SPEC.md`. Where the report says "specification", a reader
      will check; the two documents must not disagree.
- [ ] Decide the licence: CC-BY-4.0 for text is already stated. arXiv lets you pick a licence at
      submission; choose the same one.

## 2. Convert

```bash
pandoc docs/paper/aura-agf.md -o aura-agf.pdf \
  --pdf-engine=xelatex \
  -V geometry:margin=1in \
  -V fontsize=11pt \
  -V linkcolor=blue \
  --number-sections=false
```

`--number-sections=false` keeps the hand-written section numbers; if you prefer automatic
numbering, drop it and remove the numbers from the headings.

## 3. arXiv metadata

| field | suggestion |
|---|---|
| primary category | `cs.SE` (software engineering) or `cs.MS` (mathematical software) |
| cross-list | `cs.HC` if you keep the authoring-surface framing |
| title | Aura Graph Format: a typed, port-addressed document model for mathematics |
| abstract | the report's Abstract, unchanged — it already states the limits |
| comments | `Technical report. Specification: <DOI>. Code: <DOI>.` |
| ACM/MSC class | optional; skip if unsure |

**Endorsement.** First-time submitters to most `cs.*` categories need an endorsement from an
established arXiv author (an endorsement code). This is normal and is not an editorial judgement.
If you cannot find one, the same report works as a preprint on SSRN, TechRxiv or as a tagged
release with a DOI, and a DOI is what most citation systems need.

## 4. After it appears

- Add the arXiv id to `docs/CITATION.cff` and the README.
- Post it where the audience already is (the SymPy mailing list, a math-education forum) with a
  one-line ask: *"does the document model survive your corner cases?"* — that question is what
  produces the third implementation.
- Do not present the report as evidence that AGF is a standard. It is evidence that the format is
  specified, implemented twice and tested. §9 says so explicitly.
