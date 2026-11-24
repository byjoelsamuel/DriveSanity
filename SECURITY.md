# Security

DriveSanity reads your car's data over a read-only OBD2 link and your phone's location. It sends nothing to
any account, and only fetches map tiles, routes, addresses and speed limits from OpenStreetMap-based services, and
release notes and updates from GitHub.

## Reporting a problem

Please report a vulnerability privately, not in a public issue: open
[Security → Report a vulnerability](https://github.com/byjoelsamuel/DriveSanity/security/advisories/new) on
this repo. It's a one-person project, so expect a reply in days, not hours.

## What's checked

- Every change runs the tests, Android lint, ktlint and detekt, and CodeQL scans the Kotlin code.
- Dependabot opens pull requests for outdated libraries and GitHub Actions, and GitHub's secret scanning with
  push protection watches for leaked keys.
- Release APKs are signed with a key kept only in the repo's Actions secrets, and the in-app updater checks the
  download against the checksum GitHub publishes before offering to install it.
