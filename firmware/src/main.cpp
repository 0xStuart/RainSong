#include <Arduino.h>

#include "ble.h"
#include "drop.h"

void setup() {
    Serial.begin(115200);
    delay(400);
    Serial.println();
    Serial.println(F("RainSong fw 1.0"));
    drop_begin();
    ble_start();
}

void loop() {
    drop_poll();
    ble_poll();
}
