"""zipalign 대신: 압축하지 않은 파일(resources.arsc 등)을 4바이트 경계에 맞춰 다시 묶는다.
사용법: python3 align.py 입력.apk 출력.apk 추가할파일(classes.dex)..."""
import sys, zipfile, os

src, dst, extra = sys.argv[1], sys.argv[2], sys.argv[3:]
zin = zipfile.ZipFile(src)
with open(dst, 'wb') as f:
    zout = zipfile.ZipFile(f, 'w')
    def put(name, data, stored):
        zi = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        if stored:
            pad = (-(f.tell() + 30 + len(name.encode('utf-8')))) % 4
            zi.extra = b'\x00' * pad
        zout.writestr(zi, data)
    for zi in zin.infolist():
        put(zi.filename, zin.read(zi.filename), zi.compress_type == zipfile.ZIP_STORED)
    for p in extra:
        put(os.path.basename(p), open(p, 'rb').read(), False)
    zout.close()
