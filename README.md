# ReelsBlock

ReelsBlock is an Android app that blocks any content from people that you don't follow. You can still see any content shared to you in DMs. It runs alongside the official Instagram app and works locally on your phone, using no information except for what is on your screen.

This is a personal tool that I am making public, I have tested it on my phone (Android 16) on the current Instgarm version, but I can't guarantee it will work for old versions of Android or after an Instagram update.

## Installation

You need Android Studio, a USB cable, and a phone running **Android 8.0 or later** with Instagram installed.

1. Clone this repository or select **Code → Download ZIP** on GitHub and extract it.
2. Open the folder containing `settings.gradle.kts` in Android Studio and let Gradle sync. If prompted, install **Android SDK Platform 37**.
3. On your phone, enable **Developer options → USB debugging**. Connect it to your computer and accept the USB debugging prompt.
4. In Android Studio, select your phone, choose the **app** configuration, and click **Run**. This builds and installs ReelsBlock. On Windows, you can also install it by running `.\gradlew.bat installDebug` from the project folder in PowerShell.
5. Open **ReelsBlock**, read the accessibility disclosure, and tap **Agree and continue**.
6. In your phone’s **Settings → Accessibility → Installed apps**, select **ReelsGuard** and enable it. On some phones, this menu is called **Downloaded apps**.
7. Return to ReelsBlock and check that the service is enabled. Open Instagram and tap the Reels tab to try it.

To turn blocking off, disable **ReelsGuard** in the same Accessibility menu. I made this intentionally annoying to turn off to encourage you to just keep it on always. 

If Android prevents you from enabling a sideloaded accessibility service, see [Android’s restricted settings guide](https://support.google.com/android/answer/12623953).


## Limitations

- **Fast scrolling can briefly reveal content.** Covers take a moment to appear, and the service cannot cancel a swipe already in motion.
- **Instagram updates and language settings affect detection.** The rules rely on Instagram’s layout and English interface labels. Device testing was performed on an Android 16 phone.

ReelsBlock is an independent personal project and has no affiliation with Instagram or Meta.