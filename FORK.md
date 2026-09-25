# SchwarzDigits fork of Kalium

This repository is a fork of [wireapp/kalium](https://github.com/wireapp/kalium). `digits/main` holds Wire's `develop`
plus the few changes our releases need, and our releases are built from it. Every change goes to Wire as a pull
request, so the fork stays close to Wire.

## Branches

| Branch | What it is |
|---|---|
| `develop` | Mirror of Wire's `develop`. Fast-forward only; never commit to it. |
| `digits/main` | The default branch: `develop` plus the changes our releases need, merged. Releases are tagged here (see [Releases](#releases)). |
| `fix/<topic>` | One change per branch, based on `develop` or on the change it builds on. It goes to Wire as a pull request. |

Change branches are always named `fix/<topic>`, also for new functionality. Branches of older pull requests may still
carry another prefix.

## What goes into `digits/main`

Only changes our releases need. Today that is one change: the local databases on Apple platforms are encrypted with
SQLCipher.

Every other change lives on its `fix/<topic>` branch and goes to Wire only. It reaches `digits/main` when Wire has
merged it and Wire's `develop` is merged into `digits/main`.

## Lifecycle of a change

1. Create `fix/<topic>` from `develop`, or from the branch the change depends on. Make the change with tests and a
   changelog fragment in `changelog.d/`.
2. Go through [Before opening a pull request](#before-opening-a-pull-request).
3. Open a pull request from the branch against `develop` of `wireapp/kalium`.
4. Only if our releases need the change: merge it into `digits/main` with a merge commit, not a squash, so that
   `digits/main` carries the same commits Wire sees.
5. Once Wire has merged it, the change comes back into `digits/main` with the next merge of Wire's `develop` (see
   [Keeping up with Wire](#keeping-up-with-wire)). Delete the branch only then.

If things go differently:

- **Wire changes the change before merging it:** When Wire's `develop` is merged into `digits/main`, Wire's version
  wins, wording included.
- **Wire's `develop` moves past an open pull request and conflicts:** Rebase the branch. This rewrites a branch with an
  open pull request, so agree on it first.

## Rules

- One change per branch and per pull request. A change that needs another one is based on that branch, and its pull
  request says so.
- Start change branches from `develop` or from the change they build on, never from `digits/main`.
- Never commit to `develop`, and don't force-push a branch with an open pull request without agreeing on it first.
- Text in code, changelog fragments and commit messages must be true for Wire's code as well, and names no product or
  customer.
- Commit and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org/) as
  `fix(<scope>): …`.

## Before opening a pull request

- Run the tests of every module you changed, for example `./gradlew :logic:jvmTest --tests '*MyUseCaseTest*'`.
- Run `./gradlew detekt`.
- If the public API of `:logic` changed, run `./gradlew :logic:updateKotlinAbi` and commit the updated dumps in
  `logic/api/`. `./gradlew :logic:checkKotlinAbi` must pass.
- Add a changelog fragment in `changelog.d/` as described in `changelog.d/README.md`, with the ABI, Source, Behavior
  and Migration notes. Prefer the `.fixed.md` suffix, or `.security.md` where it applies.
- Check that the branch carries no fork-only file (see [Fork-only files](#fork-only-files)), and that Wire's CLA check
  passes.

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
git push origin digits/main
```

Conflicts:

- **In a change Wire has merged:** take Wire's version, wording included.
- **In one of our changes Wire hasn't merged:** keep our change and adapt it to Wire's new code. If its branch has an
  open pull request at Wire, that branch needs a rebase as well (see [Lifecycle of a change](#lifecycle-of-a-change)).

## Releases

A release is an annotated tag on `digits/main`, named `digits-<n>` with `n` counting up. The tag records its date; list
the releases in order with `git tag --list 'digits-*' --sort=v:refname`.

1. Merge Wire's `develop` into `digits/main` (see [Keeping up with Wire](#keeping-up-with-wire)).
2. Run the tests of the modules our changes touch.
3. Tag and push. The tag message names the commit of Wire's `develop` the release is based on, and lists our changes:

   ```sh
   git log --oneline --no-merges HEAD ^wire/develop    # our changes
   git tag -a digits-<n>
   git push origin digits-<n>
   ```

Notes:

- Tags without the `digits-` prefix belong to Wire. Never create them here.
- This fork doesn't publish Kalium to Maven. Wire's release workflows are disabled here, and a `digits-` tag doesn't
  match them anyway. Consumers build Kalium from source at a release tag.
- Kalium uses Wire's Core Crypto releases, as Wire's `develop` pins them.

## Fork-only files

`FORK.md` exists only in `digits/main`, never in `develop`, so pull requests to Wire don't carry it. Before opening a
pull request at Wire, check the branch:

```sh
git diff --name-only wire/develop...HEAD | grep -x 'FORK.md' && echo "fork-only file in this branch"
```

## AI coding agents

Wire's `.gitignore` excludes `CLAUDE.md`, `AGENTS.md` and `.claude/`, so agent instructions stay local. To give your
agent the rules of this fork:

- **Claude Code:** Create a local `CLAUDE.md` in the repository root containing the line `@FORK.md`; Claude Code then
  imports this file.
- **Other agents:** Point their local instructions to `FORK.md`.
