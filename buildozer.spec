[app]
title = OperaFast
package.name = operafast
package.domain = org.blackorange
source.dir = .
source.include_exts = py,kv,png,jpg,json,html,css,js,gz
version = 0.1.0

requirements = python3==3.10.11,hostpython3==3.10.11,kivy==2.3.0,pyjnius,android,cryptography,sqlite3

orientation = portrait
fullscreen = 0

android.permissions = INTERNET
android.api = 33
android.minapi = 26
android.ndk = 25b
android.ndk_api = 26
android.archs = arm64-v8a
android.accept_sdk_license = True
android.allow_backup = True

p4a.branch = develop
