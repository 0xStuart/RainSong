#pragma once

// See firmware/pins.md. Do not put DO on D0 (GPIO2) or D9 (GPIO9).

constexpr int PIN_PIEZO_AO = D1;  // GPIO3, ADC1_CH3
constexpr int PIN_HIT_DO = D2;    // GPIO4, digital wake
constexpr int PIN_FAKE_BTN = D9;  // GPIO9, Boot button, active low
