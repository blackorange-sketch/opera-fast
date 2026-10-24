[app]
title = OperaFast
package.name = operafast
package.domain = org.blackorange
source.dir = .
source.include_exts = py,kv,png,jpg,json,html,css,js,gz,ttf,txt
version = 0.1.0

blacklist_requirements = libthorvg
requirements = python3==3.10.11,hostpython3==3.10.11,kivy==2.2.1,pyjnius,android

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

p4a.branch = master
p4a.extra_args = --recipe-blacklist=libthorvg
