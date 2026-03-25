#include "aoa_bridge.hpp"
#include <iostream>
#include <cstring>
#include <chrono>
#include <thread>
#include <libusb.h>

namespace sentinoid {

static const char* AOA_ACCESSORY_GET_PROTOCOL = "/?api=1&mid=%s&pid=%s";
static const char* AOA_START_ACCESSORY = "/?api=1&start";

class AoaBridge::Impl {
public:
    libusb_context* ctx;
    libusb_device_handle* handle;
    bool running;
    AoaCallback event_callback;
    LogCallback log_callback;

    Impl() : ctx(nullptr), handle(nullptr), running(false) {}

    ~Impl() {
        stop();
        if (ctx) {
            libusb_exit(ctx);
        }
    }

    bool initialize() {
        int r = libusb_init(&ctx);
        if (r < 0) {
            std::cerr << "libusb_init failed: " << libusb_error_name(r) << std::endl;
            return false;
        }
        return true;
    }

    bool findAndConnectDevice() {
        libusb_device** devs;
        ssize_t cnt = libusb_get_device_list(ctx, &devs);
        if (cnt < 0) return false;

        bool found = false;
        for (ssize_t i = 0; i < cnt; i++) {
            libusb_device* dev = devs[i];
            libusb_device_descriptor desc;
            
            if (libusb_get_device_descriptor(dev, &desc) < 0) continue;

            if (desc.idVendor == AOA_VENDOR_ID && 
                (desc.idProduct == AOA_PRODUCT_ID_ACCESSORY || 
                 desc.idProduct == AOA_PRODUCT_ID_ACCESSORY_ADB)) {
                
                if (libusb_open(dev, &handle) == 0) {
                    found = true;
                    if (event_callback) {
                        event_callback(AoaEvent::DEVICE_CONNECTED, "Android device connected in AOA mode");
                    }
                }
                break;
            }
        }

        libusb_free_device_list(devs, 1);
        return found;
    }

    bool sendAccessoryString(const char* str) {
        if (!handle) return false;
        
        unsigned char buffer[64];
        strncpy((char*)buffer, str, 63);
        buffer[63] = 0;
        
        int transferred = 0;
        int r = libusb_bulk_transfer(handle, AOA_BULK_ENDPOINT_OUT, 
                                     buffer, 64, &transferred, 1000);
        return r == 0;
    }

    bool readLogData(std::vector<uint8_t>& data) {
        if (!handle) return false;

        unsigned char buffer[AOA_MAX_PACKET_SIZE];
        int transferred = 0;
        
        int r = libusb_bulk_transfer(handle, AOA_BULK_ENDPOINT_IN, 
                                     buffer, AOA_MAX_PACKET_SIZE, &transferred, 1000);
        
        if (r == 0 && transferred > 0) {
            data.assign(buffer, buffer + transferred);
            return true;
        }
        return false;
    }

    void eventLoop() {
        while (running) {
            if (!handle) {
                if (!findAndConnectDevice()) {
                    std::this_thread::sleep_for(std::chrono::seconds(1));
                    continue;
                }
            }

            std::vector<uint8_t> data;
            if (readLogData(data)) {
                if (log_callback && !data.empty()) {
                    log_callback(data);
                }
            }

            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }
};

AoaBridge::AoaBridge() : pImpl(std::make_unique<Impl>()) {}
AoaBridge::~AoaBridge() = default;

bool AoaBridge::init() {
    return pImpl->initialize();
}

void AoaBridge::setEventCallback(AoaCallback callback) {
    pImpl->event_callback = callback;
}

void AoaBridge::setLogCallback(LogCallback callback) {
    pImpl->log_callback = callback;
}

bool AoaBridge::start() {
    if (pImpl->running) return true;
    pImpl->running = true;
    return true;
}

bool AoaBridge::stop() {
    pImpl->running = false;
    if (pImpl->handle) {
        libusb_close(pImpl->handle);
        pImpl->handle = nullptr;
    }
    return true;
}

bool AoaBridge::sendData(const uint8_t* data, size_t length) {
    if (!pImpl->handle) return false;
    int transferred = 0;
    int r = libusb_bulk_transfer(pImpl->handle, AOA_BULK_ENDPOINT_OUT,
                                 const_cast<uint8_t*>(data), length, &transferred, 1000);
    return r == 0;
}

bool AoaBridge::sendString(const std::string& str) {
    return sendData((const uint8_t*)str.c_str(), str.size());
}

std::vector<AoaDevice> AoaBridge::listDevices() const {
    std::vector<AoaDevice> devices;
    return devices;
}

bool AoaBridge::isConnected() const {
    return pImpl->handle != nullptr;
}

std::string getAoaProtocolInfo() {
    return "Android Open Accessory Protocol v2.0\n"
           "Vendor ID: 0x18D1\n"
           "Product IDs: 0x2D00 (Accessory), 0x2D01 (Accessory + ADB)";
}

}
