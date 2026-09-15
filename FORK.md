# SchwarzDigits fork of Kalium

This repository is a fork of [wireapp/kalium](https://github.com/wireapp/kalium). `digits/main` holds Wire's `develop` plus our changes, and our releases are built from it. Every change that is useful for Wire goes to Wire as a pull request, so the fork stays close to Wire.

## Branches

| Branch | What it is |
|---|---|
| `develop` | Mirror of Wire's `develop`. Fast-forward only; never commit to it. |
| `digits/main` | Our consolidated state and the default branch: `develop` plus all our changes, merged. Releases are tagged here with a `-digits.N` suffix. |
| `fix/<topic>` | One change per branch, based on `develop` or on the change it builds on. It goes to Wire as a pull request and into `digits/main`. |
| `digits/<topic>` | A change that stays in this fork, because it isn't meant for Wire or Wire declined it. |

Change branches are always named `fix/<topic>`, also for new functionality.

## Lifecycle of a change

1. Create `fix/<topic>` from `develop`, or from the branch the change depends on. Make the change with tests and a changelog fragment in `changelog.d/`.
2. Open a pull request into `digits/main`. The Digits JVM tests run there on Windows, Linux and macOS, and the change is reviewed.
3. Merge it into `digits/main` with a merge commit, not a squash, so that `digits/main` carries the same commits Wire sees.
4. Open a pull request from the same branch against `develop` of `wireapp/kalium`.
5. Once Wire has merged it, the change comes back into `digits/main` with the next merge of `develop` (see below). Delete the branch only then.

If things go differently:

- **Wire changes the change before merging it:** When `develop` is merged into `digits/main`, Wire's version wins, wording included.
- **Wire declines it:** Rename the branch to `digits/<topic>`. The change stays in `digits/main`.
- **Wire's `develop` moves past an open pull request and conflicts:** Rebase the branch. This rewrites a branch with an open pull request, so agree on it first.

## Rules

- One change per branch and per pull request. A change that needs another one is based on that branch, and its pull request says so.
- The pull request into `digits/main` and the one at Wire don't reference each other.
- Text in code, changelog fragments and commit messages must be true for Wire's code as well, and names no product or customer.
- Commit and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org/) as `fix(<scope>): …`.

## Keeping up with Wire

```sh
git remote add wire https://github.com/wireapp/kalium.git    # once
git fetch wire develop
git push origin wire/develop:develop    # fast-forward our develop
git switch digits/main && git pull
git merge develop                       # conflicts: take Wire's version
git push origin digits/main
```

## CI

`Digits JVM tests` (`.github/workflows/digits-jvm-tests.yml`) runs the JVM tests of `data/persistence`, `domain/userstorage` and `core/cryptography` on Windows, Linux and macOS:

- for pushes to `digits/**`,
- for pull requests into `digits/main`.

Change branches start from Wire's `develop` and don't contain the workflow; their pull request into `digits/main` runs it. Wire's own workflows are unchanged.

## Fork-only files

These files exist only in `digits/main`, never in `develop`, so pull requests to Wire don't carry them:

- `FORK.md`,
- `.claude/` (see below),
- `.github/workflows/digits-*.yml`.

Before opening a pull request at Wire, check that the branch doesn't contain any of them:

```sh
git diff --name-only wire/develop...HEAD | grep -E '^(FORK\.md|\.claude/|\.github/workflows/digits-)' && echo "fork-only files in this branch"
```

## AI coding agents

Wire's `.gitignore` excludes `.claude/`, `CLAUDE.md` and `AGENTS.md`. This fork still tracks `.claude/CLAUDE.md`, added with `git add -f`, so Claude Code picks up these rules without any setup; it imports this file.

- Add new files under `.claude/` with `git add -f` as well.
- Personal files such as `.claude/settings.local.json` stay ignored.
- Other agents: point their local instructions to `FORK.md`.
