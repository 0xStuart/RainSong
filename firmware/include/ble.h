#pragma once

#include "protocol.h"

void ble_start();
void ble_poll();
void ble_notify_drop(const rainsong_drop_t& drop);
void ble_refresh_status();
bool ble_drop_subscribed();
