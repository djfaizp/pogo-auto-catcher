# TODO List for Root Access and Frida Gadget Integration

- [x] Add INTERNET permission to `AndroidManifest.xml`.
- [x] Add code to `MainActivity.kt` to request root permissions.
- [x] Add code to `MainActivity.kt` to load the Frida gadget library.
- [ ] Manually download the appropriate Frida gadget `.so` file (e.g., for `arm64-v8a`).
- [ ] Create the `app/src/main/jniLibs/arm64-v8a` directory (or other relevant ABI directories).
- [x] Place the downloaded `frida-gadget.so` file into the `jniLibs` directory, renaming it to `libfrida-gadget.so`.
- [x] Configure `build.gradle.kts` to include `jniLibs` if necessary.
- [ ] Add user-provided Frida hooking code later.