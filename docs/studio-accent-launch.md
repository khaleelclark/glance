# Android Studio launch after an accent change

The owner reported `Activity class ... LauncherLilac does not exist` after
selecting Teal and updating through Studio. Lilac is the manifest-default
launcher alias, but the selected Teal alias disables it on the installed device.
An IDE configuration that chooses that default alias therefore fails to launch.
The always-enabled `com.khaleel.glance.MainActivity` remains available.

The shared `.run/Glance_phone.run.xml` selects Specified Activity and targets
MainActivity. After pulling, explicitly select **Glance phone**; Studio may keep
the previous locally saved `app` configuration selected. If the module cannot
be resolved on a local import, choose the phone `app` module.

To repair an existing configuration immediately, open **Run → Edit
Configurations**, select the phone configuration, set **Launch → Specified
Activity**, enter `com.khaleel.glance.MainActivity`, Apply and Run. This preserves
the existing installation's signing key and settings; it does not require an
uninstall or clearing app data.

[Android Studio's official launch documentation](https://developer.android.com/studio/run/rundebugconfig#general)
supports Specified Activity. The configuration format is also illustrated by a
[Google Android codelab](https://github.com/googlecodelabs/android-lifecycles/blob/master/.idea/runConfigurations/Step_1.xml).
Shared project files outside `.idea` are supported by
[JetBrains project configuration documentation](https://www.jetbrains.com/help/idea/run-debug-configuration.html#share-configurations).

Root's debug assembly passes. The bounded API36 native profile passes four
checks at exact `61f6e0153e70697b21a2b84e4abfecaed708afc4`: disabled Lilac
reproduces the owner's Error type 3; replacing the same debug APK retains saved
settings/private marker; the configured explicit MainActivity starts; persisted
Teal remains the sole launcher after cold launch. The installed APK SHA256 is
`3a4874baa948d3760511843789ff4bb591fd0dc54352105aa0cdd3745c55ddd4`.
Receipt: `evidence/api36-studio-accent-launch.json`.

The initial driver stopped on the expected nonzero failed-launch status; its
corrected version checks both that status and Error type 3 before continuing.
This evidence uses a dedicated synthetic emulator and equivalent Android launch
intents, not an interactive Studio session or the owner's Samsung phone. No app
runtime, manifest, source/import behavior, versions or signing keys change in
this project-configuration fix. Independent source review is tracked.
