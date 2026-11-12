#!/usr/bin/env python3
"""
Builds app/src/main/assets/demo.dsr: a made-up drive that the app plays back in demo mode.

A Mazda3 (the app's own gear table) does a standing start and three laps of a loop on an empty
flat in the Black Rock Desert, then stops. Nothing here was recorded from a real car or a real
person's drive; it is a simulation, written in the same raw format the app records
(see RawLog.kt), so every screen, the lap timer and the 0-100 timer have something to show.

    python3 tools/make_demo.py
"""
import math
import random

random.seed(7)
G = 9.81
ORIGIN_LAT, ORIGIN_LON = 40.80, -119.10     # a salt flat; nothing is there
M_LAT = 111_320.0
M_LON = 111_320.0 * math.cos(math.radians(ORIGIN_LAT))

# --- the loop: two straights and two right-hand 180 degree turns -------------------------------
STRAIGHT = 420.0
R = 55.0
TURN = math.pi * R
LOOP = 2 * STRAIGHT + 2 * TURN
LAPS = 3
LINE_S = 250.0           # the start/finish line, on the first straight
S_START = LINE_S - 170   # the car starts 170 m before it, standing
S_END = LINE_S + LAPS * LOOP + 120


def path(s):
    """(x east, y north, heading in degrees, signed curvature) at distance s round the loop."""
    s %= LOOP
    if s < STRAIGHT:
        return s, 0.0, 90.0, 0.0
    s -= STRAIGHT
    if s < TURN:
        phi = s / R
        return STRAIGHT + R * math.sin(phi), -R + R * math.cos(phi), 90 + math.degrees(phi), 1 / R
    s -= TURN
    if s < STRAIGHT:
        return STRAIGHT - s, -2 * R, 270.0, 0.0
    s -= STRAIGHT
    phi = s / R
    return -R * math.sin(phi), -R - R * math.cos(phi), 270 + math.degrees(phi), 1 / R


def latlon(x, y):
    return ORIGIN_LAT + y / M_LAT, ORIGIN_LON + x / M_LON


# --- the car -----------------------------------------------------------------------------------
RATIOS = [3.55, 2.02, 1.35, 1.00, 0.74, 0.60]
FINAL = 3.626
ROLLING = math.pi * ((18 * 25.4 + 2 * 215 * 45 / 100) / 1000) * 0.97
KMH_PER_KRPM = [60 * ROLLING / (g * FINAL) for g in RATIOS]
REDLINE = 6300


def accel_cap(v):
    """Most the car can accelerate at speed v (m/s), in m/s^2: 0-100 km/h in about 7 s."""
    return max(0.6, 5.2 - 0.0021 * v * v)


BRAKE = 7.0
LAP_SCALE = [0.97, 1.00, 1.03]  # each lap is driven a touch differently, so the delta has something to do


def limit_speed(s):
    lap = min(LAPS - 1, max(0, int((s - LINE_S + 0.5 * LOOP) // LOOP)))
    k = LAP_SCALE[lap]
    _, _, _, curv = path(s)
    top = 38.0 * k
    if curv:
        return math.sqrt(0.38 * G * R) * k
    return top


def plan():
    """Speed against distance: as fast as the limits allow, braking early enough for the corners."""
    ds = 1.0
    n = int((S_END - S_START) / ds) + 1
    lim = [limit_speed(S_START + i * ds) for i in range(n)]
    lim[-1] = 0.0
    v = [0.0] * n
    for i in range(n - 1):
        v[i + 1] = min(lim[i + 1], math.sqrt(v[i] ** 2 + 2 * accel_cap(v[i]) * ds))
        v[i + 1] = max(v[i + 1], 0.6) if i > 0 else v[i + 1]
    v[-1] = 0.0  # it stops at the end
    for i in range(n - 2, -1, -1):
        v[i] = min(v[i], math.sqrt(v[i + 1] ** 2 + 2 * BRAKE * ds))
    return v, ds


def main():
    vplan, ds = plan()

    def target(s):
        i = max(0, min(len(vplan) - 2, int((s - S_START) / ds)))
        f = (s - S_START) / ds - i
        return vplan[i] * (1 - f) + vplan[i + 1] * f

    out = []
    out.append("# DriveSanity demo drive. A simulation, not a recording of a real car.")
    out.append("# Mazda3 on an empty flat: a standing start and three laps of a loop, then a stop.")
    x, y, hd, _ = path(LINE_S)
    la, lo = latlon(x, y)
    out.append("# line,Demo loop,%.6f,%.6f,%.1f" % (la, lo, hd))

    dt = 0.05
    t = 0.0
    s = S_START
    v = 0.0
    gear = 0
    gear_since = -9.0
    lat_g = 0.0
    last = {}
    next_fix = 0.0
    cool, intake = 87.0, 27.0
    idle_end = 3.0               # the car sits still for a few seconds first
    done_at = None

    def emit(ms, name, val, every=0.0, key=None):
        k = key or name
        prev = last.get(k)
        if prev is None or abs(prev[1] - val) > 1e-9 and (ms - prev[0]) >= every * 1000 or (ms - prev[0]) > 2500:
            last[k] = (ms, val)
            out.append("O,%d,%s,%s" % (ms, name, ("%d" % val) if abs(val - round(val)) < 1e-9 else ("%.2f" % val)))

    step = 0
    crossings = []
    prev_s = s
    while True:
        ms = int(round(t * 1000))
        if t < idle_end:
            v = 0.0
            a = 0.0
            pedal = 0.0
        else:
            tgt = target(s + max(1.5, v * dt * 3))  # look a little ahead, so it can pull away from a standstill
            if tgt > v:
                a_new = min(accel_cap(v), (tgt - v) / dt)
            else:
                a_new = -min(BRAKE, (v - tgt) / dt)
            v_new = max(0.0, v + a_new * dt)
            a = (v_new - v) / dt
            v = v_new
            s += v * dt
            if a > 0.2:
                pedal = min(100.0, 30 + 70 * (a / accel_cap(v)) ** 0.8)
            elif a < -0.5:
                pedal = 0.0
            else:
                pedal = 6 + 0.28 * v * 3.6 / 3.6 * 1.0
        for k in range(LAPS + 1):
            if prev_s < LINE_S + k * LOOP <= s:
                crossings.append(t)
        prev_s = s
        kmh = v * 3.6
        _, _, hd, curv = path(s)
        lat_target = v * v * curv / G
        lat_g += (lat_target - lat_g) * (dt / 0.4)

        # gear: shift up near the top of the revs when pushing, earlier when easy
        up_rpm = 2800 + pedal / 100 * 3300
        want = 0
        for g in range(6):
            if kmh / KMH_PER_KRPM[g] * 1000 < up_rpm:
                want = g
                break
            want = g
        if gear == 0 and v > 0:
            gear = 0
        if want != gear and t - gear_since > 0.8:
            gear = want
            gear_since = t
        rpm = max(750.0, kmh / KMH_PER_KRPM[gear] * 1000)
        if kmh < 35:  # the torque converter slips off the line
            rpm = max(rpm, 900 + 22 * pedal if pedal > 15 else rpm)
        rpm = min(rpm, REDLINE + 150) + random.uniform(-12, 12) * (1 if rpm < 1200 else 0.3)

        if step % 2 == 0:  # ten a second
            emit(ms, "rpm", round(rpm))
            emit(ms, "speed", round(kmh))
            emit(ms, "pedal", round(pedal))
            out.append("G,%d,%.3f,%.3f" % (ms, a / G, lat_g))
        if step % 10 == 0:  # twice a second... and the slower ones
            valve = 11 + 0.62 * pedal
            emit(ms, "throttle", round(valve))
            load = min(100.0, 18 + 0.78 * pedal + 0.002 * rpm)
            emit(ms, "load", round(load))
            maf = rpm * 0.0246 * (load / 100.0) * 1.05
            emit(ms, "maf", round(maf, 1))
            emit(ms, "lambda", 0.87 if pedal > 88 else 1.0)
        if step % 20 == 0:
            cool += (92 - cool) * 0.01
            intake += (36 - intake) * 0.01
            emit(ms, "coolant", round(cool))
            emit(ms, "intake", round(intake))
            emit(ms, "volts", round(14.0 + random.uniform(-0.08, 0.08), 1))
        if step % 200 == 0:
            emit(ms, "fuel", 63)

        if t >= next_fix:  # a GPS fix a second
            x, y, hd2, _ = path(s)
            la, lo = latlon(x, y)
            out.append("F,%d,%.6f,%.6f,%.2f,%.1f,3" % (ms, la, lo, v, hd2))
            next_fix += 1.0

        if (s >= S_END - 8 and v < 0.2) or t > 600:
            if done_at is None:
                done_at = t
            if t - done_at > 2.0 or t > 600:
                break
        t += dt
        step += 1

    with open("app/src/main/assets/demo.dsr", "w") as f:
        f.write("\n".join(out) + "\n")
    print("wrote", len(out), "lines;", round(t, 1), "s")
    print("lap crossings (s):", [round(c, 1) for c in crossings])


if __name__ == "__main__":
    main()
