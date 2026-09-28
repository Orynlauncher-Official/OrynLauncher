# MobileGlues third-party notice

OrynLauncher bundles the official MobileGlues 2.0.0 Android renderer binary (libmobileglues.so) from MobileGL-Dev.

- Upstream renderer: https://github.com/MobileGL-Dev/MobileGlues
- Plugin/distribution project: https://github.com/MobileGL-Dev/MobileGlues-plugin
- Binary release: https://github.com/MobileGL-Dev/MobileGlues-release/releases/tag/V2.0.0
- License: GNU LGPL-2.1
- Bundled library: libmobileglues.so

The launcher does not modify the MobileGlues binary. The build downloads the upstream 2.0.0 APK and extracts the ABI-specific libmobileglues.so files for packaging.

The complete LGPL-2.1 license text is available from the upstream MobileGlues project. Source corresponding to the bundled renderer is available from the upstream repositories above.
