# WorkSandbox 1.0.4 security verification — 2026-11-16

The application remains com.mysandbox.work, versionName 1.0.4, versionCode 3.

## Changes

- AGP 8.4.2 -> 8.13.2, Android Build Tools pinned to 36.0.0; Gradle stays 8.14.5 with SHA-256 verification.
- AGP classpath security overrides: Protobuf 3.25.8, Guava 33.7.2-jre, Commons IO 2.22.0, Commons Compress 1.28.0, jose4j 0.9.6, JDOM 2.0.6.1, Netty 4.1.137.Final and Bouncy Castle 1.85. Overrides apply to build tooling, not APK runtime configurations.
- Full resolved build/runtime dependency report: 176 Maven name/version pairs queried against OSV, zero matching advisories at audit time. Coordinates are in security-dependencies.json. This does not guarantee absence of unknown vulnerabilities.
- setup-java v6 and CodeQL v4 pinned to repository commit SHAs. Release source must be reachable from main; workflow checks ancestry before accessing signing Secrets. Branch/tag protection still needs repository policy configuration; the ancestry check cannot protect against a compromised main or modified workflow.
- Publish scripts restrict target repository and branch, require confirmation before reading/uploading signing material, disable shell tracing, clear temporary secret variables, and include all new resources and update code in the commit list.

## New feature review

Unknown-source installation remains disabled when creating a new profile. Only this profile's owner instance can alter its own DISALLOW_INSTALL_UNKNOWN_SOURCES policy. Global restrictions and other administrators are not cleared. Existing profiles retain their policy on upgrade. User confirmation is required to enable the feature. Device/One UI verification remains required.

Update checking is manual, using the fixed public HTTPS GitHub latest-release endpoint without authentication. It adds INTERNET permission and prohibits cleartext traffic. No background polling or third-party SDK is used. API redirects are rejected; responses, read times, notes and version lengths are bounded. Only stable three-part numeric releases and an uploaded APK with the exact official repository/tag/name URL are accepted. Notes are plain text. Errors and lifecycle cancellation are handled without logging response/device data.

Downloading is delegated to the browser after user confirmation. The app does not request package-install permission, run remote code, silently install, or relax any policy as part of an update. Android verifies the APK signing certificate. Browser/source installation authorization remains under system control. No embedded token or hardcoded secret was added.

## Validation

Run scripts/test-update-policy.sh for numeric version ordering, equal/older versions, malformed/oversized versions and hostile URL/tag rejection. GitHub CI runs this test before builds.

Local test/lint/Debug build validation is recorded in the release audit. Existing Gradle unit-test tasks have no sources; the policy test is a separate JVM test. Production APK is built with the existing GitHub signing Secrets, then its signing certificate must be compared with v1.0.3. Do not replace the original release key. A full upgrade on Samsung hardware is still a required manual validation; compilation and certificate compatibility are not a device test.

## Operational recommendations

Restrict tag creation and protect main; consider a protected release environment with required reviewers. Keep original signing materials backed up offline. Increment versionCode for every future update. Never advise users to uninstall the profile owner or delete work profiles to upgrade. GitHub release tags/assets should not be replaced after distribution; this v1.0.4 tag had not existed when the release was prepared.
