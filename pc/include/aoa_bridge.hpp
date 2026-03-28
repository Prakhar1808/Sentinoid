#ifndef AOA_BRIDGE_H
#define AOA_BRIDGE_H

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace sentinoid {

constexpr uint16_t AOA_VENDOR_ID = 0x18D1;
constexpr uint16_t AOA_PRODUCT_ID_ACCESSORY = 0x2D00;
constexpr uint16_t AOA_PRODUCT_ID_ACCESSORY_ADB = 0x2D01;

constexpr int AOA_BULK_ENDPOINT_OUT = 0x01;
constexpr int AOA_BULK_ENDPOINT_IN = 0x81;

constexpr size_t AOA_MAX_PACKET_SIZE = 16384;

enum class AoaEvent {
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED,
    DATA_RECEIVED,
    ERROR
};

struct AoaDevice {
    uint16_t vendor_id;
    uint16_t product_id;
    std::string manufacturer;
    std::string model;
    std::string serial;
    bool accessory_mode;
};

using AoaCallback = std::function<void(AoaEvent, const std::string&)>;
using LogCallback = std::function<void(const std::vector<uint8_t>&)>;

class AoaBridge {
public:
    AoaBridge();
    ~AoaBridge();

    bool init();
    void setEventCallback(AoaCallback callback);
    void setLogCallback(LogCallback callback);
    
    bool start();
    bool stop();
    
    bool sendData(const uint8_t* data, size_t length);
    bool sendString(const std::string& str);
    
    std::vector<AoaDevice> listDevices() const;
    bool isConnected() const;

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

std::string getAoaProtocolInfo();

}

#endif // AOA_BRIDGE_H
