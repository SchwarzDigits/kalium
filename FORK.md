# SchwarzDigits fork of Kalium

This repository is a fork of [wireapp/kalium](https://github.com/wireapp/kalium). `digits/main` holds Wire's `develop` plus our changes, and our releases are built from it. Every change that is useful for Wire goes to Wire as a pull request, so the fork stays close to Wire.

## Branches

| Branch | What it is |
|---|---|
| `develop` | Mirror of Wire's `develop`. Fast-forward only; never commit to it. |
| `digits/main` | Our consolidated state and the default branch: `develop` plus all our changes, merged. Releases are tagged here (see [Releases](#releases)). |
| `fix/<topic>` | One change per branch, based on `develop` or on the change it builds on. It goes to Wire as a pull request and into `digits/main`. |
| `digits/<topic>` | A change that stays in this fork, because it isn't meant for Wire or Wire declined it. |

Change branches are always named `fix/<topic>`, also for new functionality.

## Lifecycle of a change

1. Create `fix/<topic>` from `develop`, or from the branch the change depends on. Make the change with tests and a changelog fragment in `changelog.d/`.
2. Go through [Before opening a pull request](#before-opening-a-pull-request).
3. Open a pull request into `digits/main`. The Digits JVM tests run there on Windows, Linux and macOS, and the change is reviewed.
4. Merge it into `digits/main` with a merge commit, not a squash, so that `digits/main` carries the same commits Wire sees.
5. Open a pull request from the same branch against `develop` of `wireapp/kalium`.
6. Once Wire has merged it, the change comes back into `digits/main` with the next merge of Wire's `develop` (see [Keeping up with Wire](#keeping-up-with-wire)). Delete the branch only then.

If things go differently:

- **Wire changes the change before merging it:** When Wire's `develop` is merged into `digits/main`, Wire's version wins, wording included.
- **Wire declines it:** Rename the branch to `digits/<topic>`. The change stays in `digits/main`.
- **Wire's `develop` moves past an open pull request and conflicts:** Rebase the branch. This rewrites a branch with an open pull request, so agree on it first.

## Rules

- One change per branch and per pull request. A change that needs another one is based on that branch, and its pull request says so.
- Start change branches from `develop` or from the change they build on, never from `digits/main`. A branch from `digits/main` would carry our fork-only changes to Wire.
- Never commit to `develop`, and don't force-push a branch with an open pull request without agreeing on it first.
- The pull request into `digits/main` and the one at Wire don't reference each other.
- Text in code, changelog fragments and commit messages must be true for Wire's code as well, and names no product or customer.
- Commit and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org/) as `fix(<scope>): …`.

## Before opening a pull request

- Run the tests of every module you changed, for example `./gradlew :logic:jvmTest --tests '*MyUseCaseTest*'`. The Digits CI covers only `data/persistence`, `domain/userstorage` and `core/cryptography`, so tests of other modules, `logic` in particular, must run locally.
- Run `./gradlew detekt`.
- If the public API of `:logic` changed, run `./gradlew :logic:updateKotlinAbi` and commit the updated dumps in `logic/api/`. `./gradlew :logic:checkKotlinAbi` must pass.
- Add a changelog fragment in `changelog.d/` as described in `changelog.d/README.md`, with the ABI, Source, Behavior and Migration notes. Prefer the `.fixed.md` suffix, or `.security.md` where it applies.
- For the pull request at Wire:
  - run the check from [Fork-only files](#fork-only-files),
  - make sure Wire's CLA check passes.

## Keeping up with Wire

When:

- at least once a week,
- before every release,
- after Wire merged one of our pull requests, so its branch can be deleted.

How:

```sh
git remote add wire https://github.com/wireapp/kalium.git    # once
git fetch wire develop
git push origin wire/develop:develop    # fast-forward our develop
git switch digits/main && git pull
git merge wire/develop
git push origin digits/main             # runs the Digits JVM tests
```

Conflicts:

- **In a change Wire has merged:** take Wire's version, wording included.
- **In one of our changes Wire hasn't merged**, whether its pull request is still open at Wire or it lives on `digits/<topic>`: keep our change and adapt it to Wire's new code. If its `fix/` branch has an open pull request at Wire, that branch needs a rebase as well (see [Lifecycle of a change](#lifecycle-of-a-change)).

## Releases

A release is an annotated tag on `digits/main`, named `digits-<YYYY.MM.DD>.<n>`, for example `digits-2026.09.15.1`. `<n>` counts the releases of that day.

1. Merge Wire's `develop` into `digits/main` (see [Keeping up with Wire](#keeping-up-with-wire)).
2. Make sure the Digits JVM tests are green on `digits/main`.
3. Tag and push. The tag message names the commit of Wire's `develop` the release is based on, and lists our changes since the previous release:

   ```sh
   git log --oneline --no-merges <previous-tag>..HEAD ^wire/develop    # our changes since the previous release
   git tag -a digits-2026.09.15.1
   git push origin digits-2026.09.15.1
   ```

Notes:

- Tags without the `digits-` prefix belong to Wire. Never create them here.
- This fork doesn't publish Kalium to Maven. Wire's release workflows (`release-logic-*.yml`, `publish-maven-central.yml`) are disabled here, and a `digits-` tag doesn't match them anyway. Consumers build Kalium from source at a release tag.

## Fork-only files

These files exist only in `digits/main`, never in `develop`, so pull requests to Wire don't carry them:

- `FORK.md`,
- `.github/workflows/digits-*.yml`.

Before opening a pull request at Wire, check that the branch doesn't contain any of them:

```sh
git diff --name-only wire/develop...HEAD | grep -E '^(FORK\.md|\.github/workflows/digits-)' && echo "fork-only files in this branch"
```

## CI

`Digits JVM tests` (`.github/workflows/digits-jvm-tests.yml`) runs the JVM tests of `data/persistence`, `domain/userstorage` and `core/cryptography` on Windows, Linux and macOS:

- for pushes to `digits/**`,
- for pull requests into `digits/main`.

Change branches start from Wire's `develop` and don't contain the workflow; their pull request into `digits/main` runs it. Wire's own workflows are unchanged.

## AI coding agents

Wire's `.gitignore` excludes `CLAUDE.md`, `AGENTS.md` and `.claude/`, so agent instructions stay local. To give your agent the rules of this fork:

- **Claude Code:** Create a local `CLAUDE.md` in the repository root containing the line `@FORK.md`; Claude Code then imports this file.
- **Other agents:** Point their local instructions to `FORK.md`.
