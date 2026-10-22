# Applingo

Android 13 added per-app languages, but the system settings only offer them for apps that declare support, and the system may clear the setting when an app updates. Applingo sets one or multiple languages for any app and keeps the settings using [Shizuku](https://shizuku.rikka.app/). 

## Features

- Set **any language** (supported by the system) for any app including languages the app doesn't list. 
- Set **a list of preferred languages** at once, and the app uses the first one it supports until it falls back to default.
- Save favourite apps and languages for easy access and management. **Batch settings** are also supported.
- Language **configurations persist**. Applingo can check language settings regularly and re-apply if the system drops them.
- Export and import language settings.
- **Adaptive UI** support for displays of different sizes and orientations including mobile / tablet / desktop / foldable.
- **Android TV** and D-pad control support.

## Screenshots

- **Portrait** orientation UI example:
  
  ![Pic1.jpg](/Screenshots/Pic1.jpg)

- **Foldable & landscape** orientation UI example:
  
  ![Pic2.jpg](/Screenshots/Pic2.jpg)
  **Note:** *The landscape orientation screenshots may slightly differ from Android TV in some screens, where extra menu items and multiple UI changes are applied for easier D-pad control.*

## Requirements

- Android 13 or newer
- Shizuku installed and service running

## Install & Usage

Download the latest apk file from [Releases](https://github.com/ernisn/Applingo/releases) and open it to install.  

1. Start Shizuku (see its [setup guide](https://shizuku.rikka.app/guide/setup/)).
2. Open Applingo and allow it to use Shizuku when asked.
3. Pick an app, tap **Change language**, choose one or more languages and tap **Apply**.
4. Restart the app if it doesn't switch right away.

To undo it later, open the app in Applingo and tap **Follow system**.

## License & Disclaimer

- [MIT](LICENSE)
- *The development of Applingo is AI-assisted.*
