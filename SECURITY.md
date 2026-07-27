# Security Policy

## Reporting a vulnerability

Please report suspected vulnerabilities **privately**. Do not open a public issue,
pull request, discussion, or chat message that contains vulnerability details,
proof-of-concept code, affected versions, or a workaround.

Preferred channel:

1. Use GitHub's **Report a vulnerability** control in this repository's Security
   tab, when it is available.

If private vulnerability reporting is unavailable, contact the maintainer through
the contact information in the [README](README.md) with only the subject
`Security report: AdvancedBiometricPromptCompat`. Do not send technical details
in that initial message; request a private channel for the report.

Please include, once a private channel is established:

- the affected artifact and version;
- a clear impact statement and the preconditions for exploitation;
- minimal, non-destructive reproduction steps or a proof of concept;
- the Android version and device/vendor context when relevant;
- any proposed mitigation, if available.

Do **not** include biometric samples or templates, credentials, private keys,
tokens, personal data, or production application data. Redact logs and screen
recordings before sharing them.

## Supported versions

Security fixes are provided for the current `2.4.*` release line and, where
feasible, for the current development branch while a release is being prepared.
Older releases are not actively supported; users should upgrade to the latest
published version before reporting an issue unless doing so would prevent
reproduction.

## Scope

This policy covers vulnerabilities in source code and official release artifacts
published by this project under the `dev.skomlach` group, including their
packaging and release process.

The following are normally outside scope:

- vulnerabilities in an integrating application, its backend, or its
  configuration;
- Android platform, device firmware, vendor services, or third-party dependency
  vulnerabilities that do not require a change in this project;
- availability-only reports that require impractical traffic volumes or physical
  access without a realistic security impact.

An issue that crosses these boundaries may still be coordinated when this
project materially contributes to the impact.

## What to expect

We aim to acknowledge a private report within 7 calendar days and provide a
status update within 14 calendar days. Triage may require follow-up questions
or a reproducible minimal case. Please allow reasonable time for validation,
fix development, testing, and release coordination.

We will handle validated reports through coordinated disclosure. Public details
will be shared only after a fix or practical mitigation is available, unless
earlier disclosure is necessary to protect users. When appropriate, a GitHub
Security Advisory and/or CVE will identify affected and fixed versions and
credit the reporter with their permission.

## Research guidelines

Good-faith research is welcome. Please avoid accessing other users' data,
interrupting services, degrading availability, or using social engineering.
Do not publicly disclose a vulnerability before coordination is complete. We
will not pursue claims based solely on research that follows these guidelines
and this policy; this statement does not authorize activity against systems
outside this project's scope.

## No bug bounty

This project does not currently offer a paid bug-bounty program. Reports are
still appreciated, and reporters may be credited in an advisory with their
consent.
