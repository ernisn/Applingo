# Applingo

Set the language of any Android app, using [Shizuku](https://shizuku.rikka.app/).

Android 13 added per-app languages, but the system settings only offer them for apps that declare support, and the system may clear the setting when an app updates. Applingo sets a language for any app and keeps it set.

## Features

- Set any language for any app, including languages the app doesn't list. 
- Set multiple languages in order as preference, for example Japanese then English, and the app uses the first one it supports.
- Persistent language configuration. Applingo can check language settings in the background and put them back if the system drops them.
- Export and import your settings.

## Requirements

- Android 13 or newer
- Shizuku, installed and running

## Install & Usage

Download the latest apk file from [Releases](https://github.com/ernisn/Applingo/releases) and open it to install.  

1. Start Shizuku (see its [setup guide](https://shizuku.rikka.app/guide/setup/)).
2. Open Applingo and allow it to use Shizuku when asked.
3. Pick an app, tap **Change language**, choose one or more languages and tap **Apply**.
4. Restart the app if it doesn't switch right away.

To undo it later, open the app in Applingo and tap **Follow system**.

## License & Disclaimer

[MIT](LICENSE). The development of Applingo was AI-assisted.
