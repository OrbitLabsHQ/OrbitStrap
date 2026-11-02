# Put OrbitStrap into your GitHub repository

This archive contains the project files. It is not an APK.

## Easiest route from Android

1. Download and extract `OrbitStrap-Android-v1.2.0.zip`.
2. Open your GitHub repository in the browser or GitHub app.
3. Upload the **contents inside the `OrbitStrap` folder** to the repository root.
   - The repository root should contain `settings.gradle.kts`, `build.gradle.kts`,
     `gradle.properties`, `app/`, and `.github/`.
   - Do not upload the outer ZIP as a single file.
   - Do not create an extra nested `OrbitStrap/OrbitStrap/` folder.
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab.
6. Select **OrbitStrap Android CI** and inspect the latest run.
7. When the run succeeds, open its run page and download the
   `OrbitStrap-1.2.0-debug-apk` artifact.

## Before replacing an existing repository

- Make a backup or create a new branch first.
- Do not delete your existing working source until the new workflow succeeds.
- If Actions fails, open the failed job and copy the **first actual compiler error**
  (not just the final `BUILD FAILED` line).

## What a green CI run proves

The workflow runs JVM unit tests, Android lint and a debug APK build using JDK 17,
Android SDK Platform 35 and Gradle 8.9. A green run proves those CI checks passed.
It does not replace installation and runtime testing on your Samsung phone.
