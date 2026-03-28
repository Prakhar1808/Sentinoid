#include "security_engine.hpp"
#include <iostream>
#include <sstream>
#include <chrono>
#include <ctime>
#include <iomanip>

namespace sentinoid {

class SecurityEngine::Impl {
public:
    LlmClient llm_client;
    AoaBridge aoa_bridge;
    AlertCallback alert_callback;
    LogAnalysisCallback analysis_callback;
    bool running;
    int alert_count;
    std::vector<SecurityAlert> recent_alerts;

    Impl() : running(false), alert_count(0) {}

    void onLogReceived(const std::vector<uint8_t>& data) {
        std::string log_data(data.begin(), data.end());
        
        if (log_data.empty()) return;

        auto response = llm_client.analyzeLog(log_data);

        if (analysis_callback) {
            analysis_callback(log_data, response);
        }

        if (response.success && response.threat_level >= 7) {
            SecurityAlert alert;
            alert.score = response.threat_level;
            alert.level = static_cast<ThreatLevel>(response.threat_level / 2);
            alert.attack_vectors = response.vectors;
            alert.mitigation = response.mitigation;
            alert.raw_log = log_data;
            alert.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();

            recent_alerts.push_back(alert);
            alert_count++;

            if (recent_alerts.size() > 100) {
                recent_alerts.erase(recent_alerts.begin());
            }

            if (alert_callback) {
                alert_callback(alert);
            }
        }
    }
};

SecurityEngine::SecurityEngine() : pImpl(std::make_unique<Impl>()) {}
SecurityEngine::~SecurityEngine() = default;

bool SecurityEngine::init() {
    if (!pImpl->llm_client.init()) {
        std::cerr << "Warning: Ollama not available, running in passive mode" << std::endl;
    } else {
        auto tier = pImpl->llm_client.detectHardware();
        pImpl->llm_client.selectModel(tier);
        std::cout << "Detected hardware: " << hardwareTierToString(tier) << std::endl;
    }

    if (!pImpl->aoa_bridge.init()) {
        std::cerr << "Warning: AOA Bridge initialization failed" << std::endl;
        return false;
    }

    pImpl->aoa_bridge.setLogCallback([this](const std::vector<uint8_t>& data) {
        pImpl->onLogReceived(data);
    });

    pImpl->aoa_bridge.setEventCallback([](AoaEvent event, const std::string& msg) {
        std::cout << "AOA Event: " << msg << std::endl;
    });

    return true;
}

bool SecurityEngine::start() {
    pImpl->running = true;
    pImpl->aoa_bridge.start();
    return true;
}

bool SecurityEngine::stop() {
    pImpl->running = false;
    pImpl->aoa_bridge.stop();
    return true;
}

void SecurityEngine::setAlertCallback(AlertCallback callback) {
    pImpl->alert_callback = callback;
}

void SecurityEngine::setLogAnalysisCallback(LogAnalysisCallback callback) {
    pImpl->analysis_callback = callback;
}

void SecurityEngine::onLogReceived(const std::vector<uint8_t>& log_data) {
    pImpl->onLogReceived(log_data);
}

LlmClient& SecurityEngine::getLlmClient() {
    return pImpl->llm_client;
}

AoaBridge& SecurityEngine::getAoaBridge() {
    return pImpl->aoa_bridge;
}

int SecurityEngine::getAlertCount() const {
    return pImpl->alert_count;
}

std::vector<SecurityAlert> SecurityEngine::getRecentAlerts(size_t count) const {
    size_t start = pImpl->recent_alerts.size() > count ? 
                   pImpl->recent_alerts.size() - count : 0;
    return std::vector<SecurityAlert>(pImpl->recent_alerts.begin() + start, 
                                      pImpl->recent_alerts.end());
}

void SecurityEngine::clearAlerts() {
    pImpl->recent_alerts.clear();
    pImpl->alert_count = 0;
}

std::string threatLevelToString(ThreatLevel level) {
    switch (level) {
        case ThreatLevel::SAFE: return "SAFE";
        case ThreatLevel::LOW: return "LOW";
        case ThreatLevel::MEDIUM: return "MEDIUM";
        case ThreatLevel::HIGH: return "HIGH";
        case ThreatLevel::CRITICAL: return "CRITICAL";
        case ThreatLevel::LOCKDOWN: return "LOCKDOWN";
        default: return "UNKNOWN";
    }
}

std::string generateSecurityReport(const std::vector<SecurityAlert>& alerts) {
    std::ostringstream report;
    
    report << "=== Sentinoid Security Report ===\n";
    report << "Total Alerts: " << alerts.size() << "\n\n";

    for (const auto& alert : alerts) {
        report << "[" << threatLevelToString(alert.level) << "] ";
        report << "Score: " << alert.score << "/10\n";
        
        if (!alert.attack_vectors.empty()) {
            report << "Vectors: ";
            for (const auto& v : alert.attack_vectors) {
                report << v << ", ";
            }
            report << "\n";
        }
        
        if (!alert.mitigation.empty()) {
            report << "Mitigation: " << alert.mitigation << "\n";
        }
        
        report << "---\n";
    }

    return report.str();
}

}
