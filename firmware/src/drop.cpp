#include "drop.h"

#include <Arduino.h>

#include "ble.h"
#include "pins.h"

static constexpr uint32_t kDebounceUs = 30000;
static constexpr uint16_t kHitMv = 80;
static constexpr uint16_t kClipMv = 3000;

static uint16_t g_baseline_mv = 0;
static int g_idle_do = LOW;
static int g_last_do = LOW;
static int g_last_btn = HIGH;
static bool g_window = false;
static uint32_t g_t0_us = 0;
static uint32_t g_last_sample_us = 0;
static uint16_t g_peak_energy = 0;
static uint8_t g_flags = 0;
static uint8_t g_seq = 0;

static uint16_t read_ao_mv() {
    analogRead(PIN_PIEZO_AO);
    return analogReadMilliVolts(PIN_PIEZO_AO);
}

static uint16_t energy_of(uint16_t mv) {
    const int delta = static_cast<int>(mv) - static_cast<int>(g_baseline_mv);
    return static_cast<uint16_t>(delta < 0 ? -delta : delta);
}

static uint16_t strength_of(uint16_t energy, bool clip) {
    if (clip) {
        return 65535;
    }
    if (energy <= kHitMv) {
        return 0;
    }
    const uint32_t span = kClipMv - kHitMv;
    uint32_t scaled = (static_cast<uint32_t>(energy - kHitMv) * 65535u) / span;
    if (scaled > 65535u) {
        scaled = 65535u;
    }
    return static_cast<uint16_t>(scaled);
}

static void open_window(uint32_t t_us, uint16_t energy, uint8_t flags) {
    if (!g_window) {
        g_window = true;
        g_t0_us = t_us;
        g_peak_energy = energy;
        g_flags = flags;
    } else {
        if (energy > g_peak_energy) {
            g_peak_energy = energy;
        }
        g_flags |= flags;
    }
    g_last_sample_us = t_us;
}

static void flush_window() {
    if (!g_window) {
        return;
    }
    rainsong_drop_t drop{};
    drop.t_us = g_t0_us;
    drop.strength = strength_of(g_peak_energy, g_flags & DROP_FLAG_CLIP);
    drop.seq = g_seq++;
    drop.flags = g_flags;
    g_window = false;

    Serial.printf("drop t=%lu us  str=%u  seq=%u  flags=0x%02x%s\n",
                  static_cast<unsigned long>(drop.t_us), drop.strength, drop.seq,
                  drop.flags, ble_drop_subscribed() ? "" : " (no cccd)");
    ble_notify_drop(drop);
}

void drop_begin() {
    pinMode(PIN_HIT_DO, INPUT);
    pinMode(PIN_PIEZO_AO, INPUT);
    pinMode(PIN_FAKE_BTN, INPUT_PULLUP);
    analogReadResolution(12);
    analogSetPinAttenuation(PIN_PIEZO_AO, ADC_11db);

    uint32_t sum = 0;
    for (int i = 0; i < 16; ++i) {
        sum += read_ao_mv();
        delay(2);
    }
    g_baseline_mv = static_cast<uint16_t>(sum / 16);
    g_idle_do = digitalRead(PIN_HIT_DO);
    g_last_do = g_idle_do;
    Serial.printf("drop baseline %u mV  idle DO=%u\n", g_baseline_mv, g_idle_do);
}

void drop_poll() {
    const uint32_t t_us = micros();
    const uint16_t mv = read_ao_mv();
    const uint16_t energy = energy_of(mv);
    uint8_t flags = 0;
    if (mv >= kClipMv) {
        flags |= DROP_FLAG_CLIP;
    }

    const int hit = digitalRead(PIN_HIT_DO);
    if (hit != g_last_do) {
        g_last_do = hit;
        if (hit != g_idle_do) {
            open_window(t_us, energy, flags);
        }
    }

    if (energy >= kHitMv) {
        open_window(t_us, energy, flags);
    } else if (!g_window) {
        g_baseline_mv = static_cast<uint16_t>((g_baseline_mv * 15u + mv) / 16u);
    }

    const int btn = digitalRead(PIN_FAKE_BTN);
    if (btn == LOW && g_last_btn == HIGH) {
        open_window(t_us, 400, 0);
    }
    g_last_btn = btn;

    if (g_window && (uint32_t)(t_us - g_last_sample_us) >= kDebounceUs) {
        flush_window();
    }
}
