#pragma once

#include <stdint.h>

// UUIDs from docs/protocol.md. Do not invent new ones.

static constexpr char RAINSONG_SERVICE_UUID[] = "1e968972-3c13-443c-ad79-a41645d56e75";
static constexpr char RAINSONG_DROP_UUID[] = "81a2f805-d83b-4214-b5d1-895642ccaa8f";
static constexpr char RAINSONG_STATUS_UUID[] = "5c471e86-7a6e-45b2-9434-bc440c0a8dfe";

static constexpr char RAINSONG_GAP_NAME[] = "RainSong";

static constexpr uint8_t RAINSONG_FW_MAJOR = 1;
static constexpr uint8_t RAINSONG_FW_MINOR = 0;

enum : uint8_t {
    DROP_FLAG_CLIP = 1u << 0,
    DROP_FLAG_WOKE = 1u << 1,
};

enum : uint8_t {
    STATUS_FLAG_SENSING = 1u << 0,
    STATUS_FLAG_LOW_BATT = 1u << 1,
};

typedef struct __attribute__((packed)) {
    uint32_t t_us;
    uint16_t strength;
    uint8_t seq;
    uint8_t flags;
} rainsong_drop_t;

typedef struct __attribute__((packed)) {
    uint16_t battery_mv;
    uint8_t fw_major;
    uint8_t fw_minor;
    uint8_t flags;
    uint8_t reserved;
} rainsong_status_t;

static_assert(sizeof(rainsong_drop_t) == 8, "drop is 8 bytes");
static_assert(sizeof(rainsong_status_t) == 6, "status is 6 bytes");
