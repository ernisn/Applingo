# Project-specific R8 rules.

# Shizuku starts the user service by class name in its own process; keep the class and constructor.
-keep class io.github.ernisn.applingo.service.ApplingoUserService {
    public <init>(...);
}
