"""Generate keyboard_tap.wav and keyboard_return.wav for GamepadMouse."""
import math
import struct
import wave

RATE = 44100


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = b"".join(
            struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples
        )
        w.writeframes(frames)


def env(n, attack, decay):
    a = min(n / attack, 1.0)
    d = math.exp(-n / decay)
    return a * d


# keyboard_tap: sharp short click (~45ms), bright
tap = []
for n in range(int(RATE * 0.045)):
    t = n / RATE
    e = env(n, 0.0015, 0.011)
    s = (
        math.sin(2 * math.pi * 1800 * t) * 0.55
        + math.sin(2 * math.pi * 3600 * t) * 0.25
        + math.sin(2 * math.pi * 5400 * t) * 0.15
    )
    tap.append(s * e * 0.7)
write_wav("keyboard_tap.wav", tap)

# keyboard_return: soft descending thock (~120ms)
ret = []
dur = int(RATE * 0.12)
for n in range(dur):
    t = n / RATE
    e = env(n, 0.002, 0.035)
    f = 520 - 240 * (n / dur)
    s = math.sin(2 * math.pi * f * t) * 0.6 + math.sin(2 * math.pi * f * 2 * t) * 0.2
    ret.append(s * e * 0.65)
write_wav("keyboard_return.wav", ret)

print("wrote keyboard_tap.wav and keyboard_return.wav")
