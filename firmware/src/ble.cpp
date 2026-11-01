#include "ble.h"

#include <NimBLEDevice.h>

static NimBLEServer* g_server = nullptr;
static NimBLECharacteristic* g_drop = nullptr;
static NimBLECharacteristic* g_status = nullptr;
static bool g_status_cccd = false;
static uint32_t g_last_status_ms = 0;
static uint8_t g_last_status_flags = 0xFF;

class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) override {
        // 7.5–15 ms, latency 0, supervision 4000 ms (units: 1.25 ms / 10 ms).
        server->updateConnParams(desc->conn_handle, 6, 12, 0, 400);
        Serial.printf("ble connect %s\n",
                      NimBLEAddress(desc->peer_ota_addr).toString().c_str());
    }

    void onDisconnect(NimBLEServer* server) override {
        (void)server;
        g_status_cccd = false;
        Serial.println(F("ble disconnect, advertising"));
        NimBLEDevice::startAdvertising();
    }
};

class StatusCallbacks : public NimBLECharacteristicCallbacks {
    void onSubscribe(NimBLECharacteristic* chr, ble_gap_conn_desc* desc,
                     uint16_t subValue) override {
        (void)chr;
        (void)desc;
        g_status_cccd = (subValue & 0x01) != 0;
        if (g_status_cccd) {
            ble_refresh_status();
            if (g_status->getSubscribedCount() > 0) {
                g_status->notify();
            }
            g_last_status_ms = millis();
        }
    }

    void onRead(NimBLECharacteristic* chr) override {
        (void)chr;
        ble_refresh_status();
    }
};

static rainsong_status_t make_status() {
    rainsong_status_t s{};
    s.battery_mv = 0;  // XIAO C3 has no VBAT ADC
    s.fw_major = RAINSONG_FW_MAJOR;
    s.fw_minor = RAINSONG_FW_MINOR;
    s.flags = STATUS_FLAG_SENSING;
    s.reserved = 0;
    return s;
}

void ble_refresh_status() {
    if (g_status == nullptr) {
        return;
    }
    const rainsong_status_t s = make_status();
    g_status->setValue(reinterpret_cast<const uint8_t*>(&s), sizeof(s));
}

void ble_start() {
    NimBLEDevice::init(RAINSONG_GAP_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    NimBLEDevice::setSecurityAuth(false, false, false);

    g_server = NimBLEDevice::createServer();
    g_server->setCallbacks(new ServerCallbacks());
    g_server->advertiseOnDisconnect(true);

    NimBLEService* svc = g_server->createService(RAINSONG_SERVICE_UUID);

    g_drop = svc->createCharacteristic(RAINSONG_DROP_UUID, NIMBLE_PROPERTY::NOTIFY);
    g_status = svc->createCharacteristic(
        RAINSONG_STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    g_status->setCallbacks(new StatusCallbacks());
    ble_refresh_status();

    svc->start();

    NimBLEAdvertisementData adv;
    adv.setFlags(ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT);
    adv.setCompleteServices(NimBLEUUID(RAINSONG_SERVICE_UUID));

    NimBLEAdvertisementData scan;
    scan.setName(RAINSONG_GAP_NAME);

    NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
    advertising->setAdvertisementData(adv);
    advertising->setScanResponseData(scan);
    advertising->setMinInterval(160);  // 100 ms
    advertising->setMaxInterval(320);  // 200 ms
    advertising->start();

    Serial.println(F("ble advertising RainSong"));
}

void ble_notify_drop(const rainsong_drop_t& drop) {
    if (g_drop == nullptr || g_drop->getSubscribedCount() == 0) {
        return;
    }
    g_drop->setValue(reinterpret_cast<const uint8_t*>(&drop), sizeof(drop));
    g_drop->notify();
}

bool ble_drop_subscribed() {
    return g_drop != nullptr && g_drop->getSubscribedCount() > 0;
}

void ble_poll() {
    const uint8_t flags = STATUS_FLAG_SENSING;
    const uint32_t now = millis();
    const bool due = (now - g_last_status_ms) >= 10000;
    const bool changed = flags != g_last_status_flags;
    if (g_status_cccd && (due || changed)) {
        ble_refresh_status();
        if (g_status->getSubscribedCount() > 0) {
            g_status->notify();
        }
        g_last_status_ms = now;
        g_last_status_flags = flags;
    }
}
