"""dx 1.7은 자바 8 클래스 파일(버전 52)을 못 읽어서(자바 6인 50까지만 읽어요), 람다 같은 자바 8 기능을 쓰지 않은 클래스의 버전을 50으로 낮춘다."""
import sys, os
for root, _, files in os.walk(sys.argv[1]):
    for n in files:
        if n.endswith('.class'):
            p = os.path.join(root, n)
            b = bytearray(open(p, 'rb').read())
            assert b[:4] == b'\xca\xfe\xba\xbe'
            if b[7] > 50:
                b[7] = 50
                open(p, 'wb').write(b)
