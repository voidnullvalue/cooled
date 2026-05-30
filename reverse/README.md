# APK Reverse Engineering Workspace

The original APK was downloaded from `https://files.catbox.moe/ou78sl.apk` and normalized locally to `reverse/base.apk` before decompilation, per the requested workflow.

Local artifact details:

- `reverse/base.apk` size: 169 MiB download, stored locally only and ignored by Git.
- `reverse/base.apk` file type: `Android package (APK), with AndroidManifest.xml`.
- `reverse/base.apk` SHA-256: `41aca043bd9ea6be4dd01476b1ba5bad21228e9e4c03afbbe423f75f1ce27fc1`.
- JADX was unavailable in this environment.
- `apktool` was installed from Ubuntu packages and used to decompile the APK to `reverse/apktool`.

The binary APK and decompiled output are intentionally excluded from version control because they are generated local artifacts and are too large/noisy for review. Re-run the download and `apktool d -f reverse/base.apk -o reverse/apktool` commands to recreate them.
