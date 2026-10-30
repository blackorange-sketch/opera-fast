# No reflection and no third-party libraries: the default Android rules are enough.

# JS bridge used to offer saving logins
-keepattributes JavascriptInterface
-keepclassmembers class org.blackorange.operafast.WebFactory$PassBridge {
    @android.webkit.JavascriptInterface <methods>;
}
