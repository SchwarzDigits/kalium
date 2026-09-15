# SchwarzDigits fork of Kalium

This is SchwarzDigits' fork of wireapp/kalium. Its branch model and rules are in `FORK.md`:

@../FORK.md

When working here:

- Start a change on a `fix/<topic>` branch from `develop`, never from `digits/main`. Only then can it go to Wire without our fork-only files.
- Before a pull request goes to wireapp/kalium, run the check from "Fork-only files" in `FORK.md`.
- Never force-push a branch that has an open pull request, and never commit to `develop`, without asking first.
- Pull requests into `digits/main` and at Wire must not mention each other.
