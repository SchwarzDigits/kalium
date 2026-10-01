# SchwarzDigits fork of Kalium

This repository is a fork of [wireapp/kalium](https://github.com/wireapp/kalium). Our releases are Wire's Kalium
releases plus the few changes they need, published to this fork's Maven repository (see
[Using a release](#using-a-release)). Every change that isn't specific to this fork is also a pull request to Wire, so
the fork stays close to Wire.

## Branches

| Branch | What it is |
|---|---|
| `develop` | Mirror of Wire's `develop`. Fast-forward only; never commit to it. |
| `fix/<topic>` | One change per branch, based on `develop` or on the change it builds on. It goes to Wire as a pull request. |
| `digits/<version>` | A release branch: Wire's release tag `v<version>` plus our changes and the fork-only commits. Releases are tagged here (see [Releases](#releases)). The newest one is the default branch. |

There is no integration branch. Each release branch is built from Wire's release and the change branches.

Change branches are always named `fix/<topic>`, also for new functionality. Branches of older pull requests may still
carry another prefix.

## What a release branch contains

1. **Our changes**, cherry-picked with `-x` from their `fix/<topic>` branches. Only changes our releases need. Today
   these are:
   - the local databases on Apple platforms are encrypted with SQLCipher (`feat/apple-database-encryption`),
   - Apple builds can leave AVS out (`fix/apple-build-without-avs`).

   A change that Wire's release already contains is left out.
2. **The fork-only commits**, made on the release branch itself and titled `fix(fork): …`. They never go to Wire:
   - `FORK.md`,
   - the release workflow `.github/workflows/digits-release.yml`,
   - `kalium.disableAppleAvs=true` in `gradle.properties`,
   - the publishing group `kalium.publish.group` in `buildSrc`.

So every commit on a release branch is either a cherry-pick with `-x` (our changes) or fork-only. That is how the next
release branch finds the fork-only commits (see [A new Wire release](#a-new-wire-release)).

Every other change lives on its `fix/<topic>` branch and goes to Wire only. It reaches our releases with the first Wire
release that contains it.

## Lifecycle of a change

1. Create `fix/<topic>` from `develop`, or from the branch the change depends on. Make the change with tests and a
   changelog fragment in `changelog.d/`.
2. Go through [Before opening a pull request](#before-opening-a-pull-request).
3. Open a pull request from the branch against `develop` of `wireapp/kalium`.
4. Only if our releases need the change: cherry-pick its commits with `-x` onto the next release branch, or onto the
   current one for a fix release (see [Releases](#releases)).
5. Once Wire has merged it, delete the branch. The next release branch doesn't need it any more if Wire's release
   contains it.

If things go differently:

- **Wire changes the change before merging it:** Update the `fix/<topic>` branch. The next release branch takes the
  updated commits. Once Wire has merged the change, Wire's version wins, wording included.
- **Wire's `develop` moves past an open pull request and conflicts:** Rebase the branch. This rewrites a branch with an
  open pull request, so agree on it first.
- **A change is not submitted to Wire, or Wire declines it:** It keeps its `fix/<topic>` branch and goes into the release
  branches like the others.

## Rules

- One change per branch and per pull request. A change that needs another one is based on that branch, and its pull
  request says so.
- Start change branches from `develop` or from the change they build on, never from a release branch.
- Never commit to `develop`, and don't force-push a branch with an open pull request without agreeing on it first.
- Release branches only grow. Never rebase or force-push them.
- Our changes come onto a release branch only with `git cherry-pick -x`, never as a direct commit.
- Text in code, changelog fragments and commit messages must be true for Wire's code as well, and names no product or
  customer.
- Commit and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org/) as
  `fix(<scope>): …`. Fork-only commits use `fix(fork): …`.

## Before opening a pull request

- Run the tests of every module you changed, for example `./gradlew :logic:jvmTest --tests '*MyUseCaseTest*'`.
- Run `./gradlew detekt`.
- If the public API of `:logic` changed, run `./gradlew :logic:updateKotlinAbi` and commit the updated dumps in
  `logic/api/`. `./gradlew :logic:checkKotlinAbi` must pass.
- Add a changelog fragment in `changelog.d/` as described in `changelog.d/README.md`, with the ABI, Source, Behavior
  and Migration notes. Prefer the `.fixed.md` suffix, or `.security.md` where it applies.
- Check that the branch carries no fork-only commit or file (see [Fork-only files](#fork-only-files)), and that Wire's
  CLA check passes.

## Following Wire

We follow Wire's releases, not Wire's `develop`. When Wire publishes a release we take over, we create its release
branch (see [A new Wire release](#a-new-wire-release)). In between there is nothing to do.

`develop` is only the base of the change branches, because pull requests to Wire go against Wire's `develop`.
Fast-forward it before creating or rebasing a change branch:

```sh
git remote add wire https://github.com/wireapp/kalium.git    # once
git fetch wire develop
git push origin wire/develop:develop
```

## Releases

A release is an annotated tag on a release branch `digits/<version>`, named `<version>-digits.<n>`: the version of Wire's
release, and `n` counting from 1 for each Wire version, for example `0.0.8-digits.1`. A fix release on the same branch
takes the next `n`. The tag name is also the Maven version. List the releases in order with
`git tag --list '*-digits.*' --sort=v:refname`.

### A new Wire release

For Wire's release `v<version>`, with `<previous>` the version of the current release branch:

```sh
git fetch wire tag v<version> --no-tags
git switch -c digits/<version> v<version>
git cherry-pick -x <commits>    # our changes from their fix/<topic> branches, oldest first
git cherry-pick $(git log --reverse --format=%H --no-merges --invert-grep \
  --grep='(cherry picked from commit' v<previous>..origin/digits/<previous>)    # the fork-only commits
git push origin digits/<version>
```

- The fork-only commits are all commits of the previous release branch that are not a cherry-pick with `-x`. Take them
  over without `-x`, so that they count as fork-only on the new branch as well.
- Leave out the commits of a change that Wire's release already contains.
- Never push Wire's tags here.
- Conflicts in one of our changes: adapt it to Wire's code. If its branch has an open pull request at Wire, that branch
  needs a rebase as well (see [Lifecycle of a change](#lifecycle-of-a-change)).

Then run the tests, tag (see below), and make the new branch the default branch:

```sh
gh repo edit SchwarzDigits/kalium --default-branch digits/<version>
```

### Tagging

1. Run the tests of the modules our changes touch, the Apple targets on macOS.
2. Tag and push. The tag message names Wire's release, and lists our changes:

   ```sh
   git log --oneline --no-merges HEAD ^v<version>    # our changes and the fork-only commits
   git tag -a <version>-digits.<n>
   git push origin <version>-digits.<n>
   ```

Pushing the tag runs the workflow `Digits release` (`.github/workflows/digits-release.yml`). It checks that the tag is on
`digits/<version>`, and publishes all modules as `schwarz.opensource.natrium:<module>:<tag>`, signed, to the Maven
repository (see [Using a release](#using-a-release)). It never replaces a release that is already there. It can also be
started by hand for an existing tag; its input `overwrite` replaces the files of a release, for example after a failed
upload.

Notes:

- Tags starting with `v` belong to Wire. Never create or push them here; Wire's release workflows react to them.
- The workflow needs the repository secrets `MAVEN_REPOSITORY_ACCESS_KEY_ID`, `MAVEN_REPOSITORY_SECRET_ACCESS_KEY`,
  `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY` and `SIGNING_IN_MEMORY_KEY_PASSWORD`. The access key belongs to the
  credentials group `release-publisher` of the STACKIT project that holds the bucket.
- Releases up to `0.0.7-digits.3` are also on Maven Central.
- Kalium uses Wire's Core Crypto releases, as Wire's release pins them.

### Using a release

The Maven repository is the STACKIT Object Storage bucket `natrium-repository`. Anyone can read its files; nobody can
list it or write to it without a key.

```kotlin
repositories {
    mavenCentral()
    maven("https://natrium-repository.object.storage.eu01.onstackit.cloud")
}

dependencies {
    implementation("schwarz.opensource.natrium:logic:<version>-digits.<n>")
}
```

## Fork-only files

`FORK.md`, `.github/workflows/digits-release.yml` and the line `kalium.disableAppleAvs=true` in `gradle.properties`
exist only in the release branches. Neither they nor any other `fix(fork):` commit belong on a `fix/<topic>` branch, so
pull requests to Wire don't carry them. Before opening a pull request at Wire, check the branch:

```sh
git log --oneline --grep='^fix(fork):' wire/develop..HEAD | grep . && echo "fork-only commit in this branch"
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
