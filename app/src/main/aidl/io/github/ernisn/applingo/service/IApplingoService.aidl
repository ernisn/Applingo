package io.github.ernisn.applingo.service;

// Implemented by ApplingoUserService, which Shizuku runs as shell (uid 2000) or root.
interface IApplingoService {

    // Shizuku calls transaction 16777115 when it replaces or unbinds the service.
    void destroy() = 16777114;

    int getUid() = 1;

    // Per-app locales of each package as comma-separated BCP 47 tags ("" = follows the system),
    // index-aligned with packageNames; null where the package's locales could not be read.
    String[] getApplicationLocales(in String[] packageNames, int userId) = 2;

    // Always written with fromDelegate=false so the system keeps them across app updates.
    void setApplicationLocales(String packageName, int userId, String languageTags) = 3;

    void forceStopPackage(String packageName, int userId) = 4;
}
