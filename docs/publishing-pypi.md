# Publishing `aura-agf` to PyPI — setup and release runbook

> **Current status:** version 0.1.0 was published on 2026-10-25 from GitHub release
> [`aura-agf-v0.1.0`](https://github.com/artistso/aura-cal/releases/tag/aura-agf-v0.1.0).
> A clean installation from PyPI was verified. The setup steps below are retained as the
> reproducible account and release runbook; they do not need to be repeated for 0.1.0.

The package is authored by **steven owens**, builds cleanly, passes `twine check`, and
`.github/workflows/publish-pypi.yml` tests, builds and publishes it using PyPI **Trusted
Publishing** (OIDC). Trusted Publishing means PyPI trusts a specific GitHub workflow instead
of a password-like API token. Nobody should paste a PyPI token or password into a chat, issue
or commit.

> **Why not just upload now?** A PyPI version number is permanent: even if you delete a
> release, `0.1.0` can never be uploaded again. So the order below is deliberate —
> TestPyPI first, real PyPI second.

---

## Step 0 — Merge first

Trusted Publishing only works from the workflow file on the default branch, and a manual
workflow run only appears in the Actions tab once the workflow is on `main`. So:

- [ ] Merge the PR that adds `.github/workflows/publish-pypi.yml` into `main`.

## Step 1 — Accounts (PyPI and TestPyPI are separate)

- [ ] Create an account at <https://pypi.org/account/register/> and **enable 2FA**
      (PyPI requires it for publishing). Save the recovery codes somewhere safe.
- [ ] Create a *separate* account at <https://test.pypi.org/account/register/> — TestPyPI
      is an independent system with its own user database; your PyPI login will not work
      there. (Same username is convenient.)

## Step 2 — Tell PyPI to trust this repository ("pending publisher")

Because `aura-agf` does not exist on PyPI yet, you register a *pending* trusted
publisher; the first successful publish creates the project bound to this workflow.

On PyPI: log in → your avatar → **Account settings** → scroll to **Publishing** →
**Add a new pending publisher**, and enter exactly:

| Field | Value |
|---|---|
| PyPI Project Name | `aura-agf` |
| Owner | `artistso` |
| Repository name | `aura-cal` |
| Workflow name | `publish-pypi.yml` |
| Environment name | `pypi` |

Then do the same on TestPyPI (**Account settings → Publishing → Add a new pending
publisher** at <https://test.pypi.org/manage/account/publishing/>) with one difference:

| Field | Value |
|---|---|
| PyPI Project Name | `aura-agf` |
| Owner | `artistso` |
| Repository name | `aura-cal` |
| Workflow name | `publish-pypi.yml` |
| Environment name | `testpypi` |

These five values must match `.github/workflows/publish-pypi.yml` byte-for-byte —
a mismatch fails later with an `invalid-publisher` error, which is a naming problem,
not a code problem.

## Step 3 — (Optional, recommended) Protect the GitHub environments

The workflow runs its publish jobs under GitHub *environments* named `pypi` and
`testpypi`. GitHub creates them on first use if they don't exist, but you can harden
the real one: repo **Settings → Environments → `pypi` → Required reviewers** → add
yourself. Then every real publish pauses for your one-click approval. No secrets are
needed in either environment — that is the point of Trusted Publishing.

## Step 4 — Dry run on TestPyPI

- [ ] GitHub → **Actions → Publish to PyPI → Run workflow** → branch `main`, target
      **`testpypi`**.
- [ ] When it's green, verify the uploaded package in a clean environment:

```bash
python -m venv /tmp/check && /tmp/check/bin/pip install --upgrade pip
/tmp/check/bin/pip install --index-url https://test.pypi.org/simple/ \
    --extra-index-url https://pypi.org/simple/ "aura-agf[sympy]"
/tmp/check/bin/agf --help
/tmp/check/bin/python -c "import auragf; print(auragf.__version__)"
```

The `--extra-index-url` is needed because dependencies (e.g. SymPy) live on real PyPI,
not TestPyPI. Check the rendered page at <https://test.pypi.org/project/aura-agf/> too —
author name, description, links.

If something's wrong: fix it in a PR. `0.1.0` is now burned **on TestPyPI only** (its
registry is periodically cleaned and doesn't matter); real PyPI has seen nothing yet.
Just note the second TestPyPI run of the same version will be rejected — bump the
version or accept that the next dry run skips TestPyPI.

## Step 5 — The real publish

- [ ] GitHub → **Releases → Draft a new release**
  - Tag: **`aura-agf-v0.1.0`** (create it from `main`) — the workflow checks the tag
    against `pyproject.toml` and `auragf.__version__` and refuses to build on mismatch.
  - Title: `aura-agf 0.1.0`
  - Notes: what AGF is, that it's draft 0.1, link `docs/SPEC.md` and the conformance
    results. Stay accurate: two implementations, one author, no adoption claim.
- [ ] **Publish release** → the *Publish to PyPI* workflow runs the tests and
      conformance suite again, builds, `twine check`s, then publishes (pausing for your
      approval first if you set a required reviewer in Step 3).
- [ ] Verify for real:

```bash
python -m venv /tmp/check2 && /tmp/check2/bin/pip install "aura-agf[sympy]"
/tmp/check2/bin/agf --help
```

and open <https://pypi.org/project/aura-agf/>.

## After it's live

- [ ] Update `docs/ROADMAP.md` G2 ("published on PyPI"), `docs/CITATION.cff` if you mint
      a DOI for the release (see `docs/paper/how-to-submit.md`), and the README if the
      install snippet changes. Publishing closes the *distribution* half of G2; the gate
      still wants **one outside reviewer** — a package on PyPI is an invitation, not yet
      adoption.
- [ ] Next release = bump `version` in `python/pyproject.toml` **and** `__version__` in
      `python/src/auragf/__init__.py`, repeat Steps 4–5 with the new tag.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `invalid-publisher: valid token, but no corresponding publisher` | One of the five pending-publisher fields doesn't match the workflow — most often the environment name (`pypi` vs `testpypi`) or the workflow filename. |
| Workflow doesn't appear under *Actions* | The PR adding it isn't merged to `main` yet (Step 0). |
| Build job fails on the version check | Tag isn't `aura-agf-v<pyproject version>`, or `auragf.__version__` and `pyproject.toml` disagree. |
| `File already exists` on upload | That version was already uploaded to that index. Bump the version — never try to overwrite; PyPI forbids it by design. |
| Release published but nothing ran | The tag doesn't start with `aura-agf-` (by design — Android releases must not trigger a PyPI publish). |
