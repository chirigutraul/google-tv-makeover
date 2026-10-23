# 5. Screensaver photos

The home screen shows photos from `/sdcard/Pictures/Screensavers` on the TV, a new one every 90 seconds. Until you add some, it shows a dark gradient.

## Option A: your own photos

Put landscape photos (at least 1920 px wide; calmer or darker ones keep the clock readable) in a folder, then:

```bash
TV_ADDR=$TV scripts/push-screensavers.sh ~/Pictures/tv
```

The script shrinks big photos to 1920 px on your computer first so they load fast and use little TV memory.

- `--replace` also deletes photos on the TV that aren't in the folder (use it after deleting ones you don't like)
- `--dry-run` shows what would happen

## Option B: Chromecast backgrounds

The [chromecast-backgrounds](https://github.com/dconnolly/chromecast-backgrounds) list has ~700 of the photos Google's Ambient mode used, by named photographers. For personal use only.

```bash
scripts/fetch-chromecast-backgrounds.sh 100 screensavers   # download 100 random ones (~30 MB)
# look through the folder, delete the ones you don't like, then:
TV_ADDR=$TV scripts/push-screensavers.sh screensavers --replace
```

Files are named `Photo by <photographer>.jpg`; the screensaver shows that as the caption.

## More sources

- [Google Earth View](https://earthview.withgoogle.com): ~2,600 satellite landscapes made for wallpapers
- [Unsplash](https://unsplash.com): free to use, search "landscape" or "aerial"
- [Wikimedia Commons featured pictures](https://commons.wikimedia.org/wiki/Commons:Featured_pictures): free licences
- [NASA Image Library](https://images.nasa.gov): public domain

## After pushing

The home screen reads the folder when it starts. Restart it:

```bash
adb -s $TV shell am force-stop dev.raul.tvhome && adb -s $TV shell am start -n dev.raul.tvhome/.MainActivity
```

About 0.5 MB per photo after resizing; 100–300 photos is plenty.
