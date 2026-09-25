# SchwarzDigits fork of Kalium

This repository is a fork of [wireapp/kalium](https://github.com/wireapp/kalium). Our releases are Wire's Kalium
releases plus the few changes they need, published to Maven Central. `digits/main` holds Wire's `develop` with the same
changes. Every change is prepared as a pull request for Wire, so the fork stays close to Wire.

## Branches

| Branch | What it is |
|---|---|
| `develop` | Mirror of Wire's `develop`. Fast-forward only; never commit to it. |
| `digits/main` | The default branch: `develop` plus the changes our releases need, merged. It shows what the next release contains. |
| `digits/<version>` | A release branch: Wire's release tag `v<version>` plus our changes and the fork-only commit, cherry-picked. Releases are tagged here (see [Releases](#releases)). |
| `fix/<topic>` | One change per branch, based on `develop` or on the change it builds on. It goes to Wire as a pull request. |

Change branches are always named `fix/<topic>`, also for new functionality. Branches of older pull requests may still
carry another prefix.

## What goes into `digits/main`

Only changes our releases need. Today these are:

- the local databases on Apple platforms are encrypted with SQLCipher,
- Apple builds can leave AVS out (`kalium.disableAppleAvs`).

Every other change lives on its `fix/<topic>` branch and goes to Wire only. It reaches `digits/main` when Wire has
merged it and Wire's `develop` is merged into `digits/main`.

## Lifecycle of a change

1. Create `fix/<topic>` from `develop`, or from the branch the change depends on. Make the change with tests and a
   changelog fragment in `changelog.d/`.
2. Go through [Before opening a pull request](#before-opening-a-pull-request).
3. Open a pull request from the branch against `develop` of `wireapp/kalium`.
4. Only if our releases need the change: merge it into `digits/main` with a merge commit, not a squash, so that
   `digits/main` carries the same commits Wire sees. The next release branch picks it up (see
   [Keeping up with Wire](#keeping-up-with-wire)).
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

`develop` and `digits/main` follow Wire's `develop`. Our releases follow Wire's releases: for each Wire release we take
over, a release branch `digits/<version>` starts at Wire's release tag `v<version>`.

When:

- `develop` and `digits/main`: at least once a week, and after Wire merged one of our pull requests, so its branch can be
  deleted.
- A release branch: when Wire publishes a release we take over.

`develop` and `digits/main`:

```sh
git remote add wire https://github.com/wireapp/kalium.git    # once
git fetch wire develop
git push origin wire/develop:develop    # fast-forward our develop
git switch digits/main && git pull
git merge wire/develop
git push origin digits/main
```

A release branch, for Wire's release `v<version>`:

```sh
git fetch wire tag v<version> --no-tags
git switch -c digits/<version> v<version>
git cherry-pick -x <commits>    # our changes that the release doesn't contain yet, then the fork-only commit
git push origin digits/<version>
```

- **Our changes:** the commits of the changes merged into `digits/main`, from their `fix/<topic>` branches. A change
  Wire has already merged into the release is left out.
- **The fork-only commit:** the last commit of the previous release branch. It sets `kalium.disableAppleAvs=true` in
  `gradle.properties` and adds the release workflow.
- Never push Wire's tags here.

Conflicts:

- **In a change Wire has merged:** take Wire's version, wording included.
- **In one of our changes Wire hasn't merged:** keep our change and adapt it to Wire's new code. If its branch has an
  open pull request at Wire, that branch needs a rebase as well (see [Lifecycle of a change](#lifecycle-of-a-change)).

## Releases

A release is an annotated tag on a release branch `digits/<version>`, named `<version>-digits.<n>`: the version of Wire's
release, and `n` counting up over all our releases, for example `0.0.7-digits.2`. The tag name is also the Maven version.
List the releases in order with `git tag --list '*-digits.*' --sort=v:refname`.

1. Create the release branch (see [Keeping up with Wire](#keeping-up-with-wire)).
2. Run the tests of the modules our changes touch, the Apple targets on macOS.
3. Tag and push. The tag message names Wire's release, and lists our changes:

   ```sh
   git log --oneline --no-merges HEAD ^v<version>    # our changes
   git tag -a <version>-digits.<n>
   git push origin <version>-digits.<n>
   ```

Pushing the tag runs the workflow `Digits release` (`.github/workflows/digits-release.yml`). It checks that the tag is on
`digits/<version>`, and publishes all modules to Maven Central as `schwarz.opensource.natrium:<module>:<tag>`, signed.
It can also be started by hand for an existing tag.

Notes:

- Tags starting with `v` belong to Wire. Never create or push them here; Wire's release workflows react to them.
- The workflow needs the repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
  `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY` and `SIGNING_IN_MEMORY_KEY_PASSWORD`.
- Kalium uses Wire's Core Crypto releases, as Wire's release pins them.

## Fork-only files

`FORK.md` exists only in `digits/main`. `.github/workflows/digits-release.yml` and the line `kalium.disableAppleAvs=true`
in `gradle.properties` exist in `digits/main` and in the release branches. None of them belongs on a `fix/<topic>`
branch, so pull requests to Wire don't carry them. Before opening a pull request at Wire, check the branch:

```sh
git diff --name-only wire/develop...HEAD | grep -x -e 'FORK.md' -e '.github/workflows/digits-release.yml' \
  && echo "fork-only file in this branch"
git diff wire/develop...HEAD -- gradle.properties | grep -x '+kalium.disableAppleAvs=true' && echo "fork-only setting"
```

## AI coding agents

Wire's `.gitignore` excludes `CLAUDE.md`, `AGENTS.md` and `.claude/`, so agent instructions stay local. To give your
agent the rules of this fork:

- **Claude Code:** Create a local `CLAUDE.md` in the repository root containing the line `@FORK.md`; Claude Code then
  imports this file.
- **Other agents:** Point their local instructions to `FORK.md`.
