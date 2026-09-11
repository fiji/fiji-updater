[![](https://github.com/fiji/fiji-updater/actions/workflows/build-main.yml/badge.svg)](https://github.com/fiji/fiji-updater/actions/workflows/build-main.yml)

Fiji Updater
------------

The Fiji Updater is a mechanism to update individual components of [Fiji].
It keeps users up-to-date with everything an installation is made of, including
both plugins and the core libraries those plugins need.

The Updater can handle 3rd-party update sites: anybody with write access to a
web server can
[set up their own update site](https://imagej.net/update-sites/setup) which
users can decide to follow.

For more details, see the [Updater] page on the ImageJ wiki.

History
=======

This component was previously published as `net.imagej:imagej-updater`, in the
[imagej/imagej-updater](https://github.com/imagej/imagej-updater) repository.
It was renamed because the name had stopped describing the thing: the Updater
is the mechanism that maintains Fiji installations, it encodes Fiji's directory
layout and launcher conventions, and it was called the Fiji Updater originally.

The rename also opened up the API, which had been frozen by its coordinates for
a long while. Expect 3.x to differ from 2.x wherever that made the tool better.

Platform naming conventions
===========================

The Updater uses a so-called *short platform name* for each OS+arch combo.
These names are used in the Updater's `db.xml.gz` files, as well as for
platform-specific subdirectories beneath the `jars`, `lib`, and `java` folders.

| Operating system | Architecture | Short platform name  | Long platform name |
|------------------|--------------|:--------------------:|:------------------:|
| Linux            | x86 32-bit   |       linux32        |         N/A        |
| Linux            | x86 64-bit   |       linux64        |      linux-x64     |
| Linux            | ARM 64-bit   |     linux-arm64      |     linux-arm64    |
| macOS            | x86 64-bit   |       macos64        |      macos-x64     |
| macOS            | ARM 64-bit   |     macos-arm64      |     macos-arm64    |
| Windows          | x86 32-bit   |        win32         |         N/A        |
| Windows          | x86 64-bit   |        win64         |     windows-x64    |
| Windows          | ARM 64-bit   |      win-arm64       |    windows-arm64   |
| Linux            |    any       |        linuxx¹       |         N/A        |
| macOS            |    any       |        macosx²       |         N/A        |
| Windows          |    any       |         winx³        |         N/A        |


¹ The linuxx platform is a group encompassing linux-arm64 and linux64.
² The macosx platform is a group encompassing macos-arm64 and macos64.
³ The winx platform is a group encompassing win-arm64 and win64.

The *long platform names* only appear in the filenames of
[Jaunch]-based native launcher binaries, as well as in the
filenames of the [Fiji latest downloads](https://fiji.sc/).

[Fiji]: https://fiji.sc/
[Updater]: https://imagej.net/plugins/updater
[Jaunch]: https://github.com/apposed/jaunch
