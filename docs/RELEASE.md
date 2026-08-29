# Release and signing guide

PocketWebShell publishes directly installable APKs through GitHub Releases. Every production APK must be built from `main`, signed with the canonical external key, verified with `apksigner`, and accompanied by a checksum and public certificate.

## Security model

- The JKS lives outside the repository at `%USERPROFILE%\.android\keystores\pocket-web-shell-release.jks` by default.
- Its credential is stored as a Windows DPAPI-encrypted `PSCredential` XML file. It can only be decrypted by the same Windows user on the same machine.
- `scripts/build-release.ps1` decrypts the credential in memory, exports process-scoped environment variables, invokes Gradle, and clears the values in `finally`.
- Gradle contains no passwords. Git ignores keystores, signing properties, private keys and `dist/`.
- `PocketWebShell-release-cert.pem` is public certificate material and is safe to distribute. It cannot sign an APK.

Back up the JKS and its password in a separate secure system. Losing the key makes signature-compatible updates impossible. Leaking it lets an attacker impersonate future releases.

## One-time maintainer setup

Create the release key and encrypted credential outside the repository. The bootstrap operation is intentionally not automated by a checked-in script so it cannot overwrite an existing canonical key unnoticed.

Required defaults:

```text
Keystore:   %USERPROFILE%\.android\keystores\pocket-web-shell-release.jks
Credential: %USERPROFILE%\.android\keystores\pocket-web-shell-release.credential.xml
Alias:      pocket-web-shell
Algorithm:  RSA 4096 / SHA-256
```

If either file already exists, stop and verify ownership before proceeding. Never generate a replacement key for an already published application.

## Prepare a release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the version section in `CHANGELOG.md` out of Unreleased and set the date.
3. On `dev`, run:

   ```powershell
   .\gradlew.bat testDebugUnitTest :app:assembleDebug
   ```

4. Synchronize the reviewed release commit to `main` and verify the working tree is clean.

## Build, sign and verify

From `main`:

```powershell
.\scripts\build-release.ps1 -Version 0.1.0
```

The script performs a clean signed release build, verifies the APK with `apksigner --verbose --print-certs`, exports the public certificate and writes a SHA-256 file. Expected outputs:

```text
dist/PocketWebShell-v0.1.0.apk
dist/PocketWebShell-v0.1.0.apk.sha256
dist/PocketWebShell-release-cert.pem
```

Do not modify or zipalign the APK after signing; any modification invalidates the signature.

## Tag and publish

Create an annotated tag on a commit already pushed to `main`:

```powershell
git tag -a v0.1.0 -m "PocketWebShell 0.1.0"
git push origin v0.1.0
```

Create the Release and require the remote tag to exist:

```powershell
gh release create v0.1.0 `
  .\dist\PocketWebShell-v0.1.0.apk `
  .\dist\PocketWebShell-v0.1.0.apk.sha256 `
  .\dist\PocketWebShell-release-cert.pem `
  --verify-tag `
  --title "PocketWebShell 0.1.0" `
  --notes-file .\docs\release-notes-v0.1.0.md
```

## Post-publish verification

```powershell
gh release view v0.1.0 --json url,tagName,isDraft,isPrerelease,assets
git ls-remote --heads --tags origin
```

Download the published APK into a clean directory and verify it again with the same `apksigner` command. Confirm the SHA-256 matches the uploaded checksum and the certificate digest matches the prior release.

## References

- Android app signing: https://developer.android.com/studio/publish/app-signing
- Command-line build and signing: https://developer.android.com/build/building-cmdline
- `apksigner`: https://developer.android.com/tools/apksigner
- GitHub CLI release creation: https://cli.github.com/manual/gh_release_create

