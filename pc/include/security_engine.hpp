#ifndef SECURITY_ENGINE_H
#define SECURITY_ENGINE_H

#include <string>
#include <vector>
#include <memory>
#include <functional>

#include "llm_client.hpp"
#include "aoa_bridge.hpp"

namespace sentinoid {

enum class ThreatLevel {
    SAFE = 0,
    LOW = 1,
    MEDIUM = 2,
    HIGH = 3,
    CRITICAL = 4,
    LOCKDOWN = 5
};

struct SecurityAlert {
    ThreatLevel level;
    int score;
    std::vector<std::string> attack_vectors;
    std::string mitigation;
    std::string raw_log;
    uint64_t timestamp;
};

using AlertCallback = std::function<void(const SecurityAlert&)>;
using LogAnalysisCallback = std::function<void(const std::string&, const LlmResponse&)>;

class SecurityEngine {
public:
    SecurityEngine();
    ~SecurityEngine();

    bool init();
    bool start();
    bool stop();

    void setAlertCallback(AlertCallback callback);
    void setLogAnalysisCallback(LogAnalysisCallback callback);

    void onLogReceived(const std::vector<uint8_t>& log_data);

    LlmClient& getLlmClient();
    AoaBridge& getAoaBridge();

    int getAlertCount() const;
    std::vector<SecurityAlert> getRecentAlerts(size_t count) const;
    void clearAlerts();

private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

std::string threatLevelToString(ThreatLevel level);
std::string generateSecurityReport(const std::vector<SecurityAlert>& alerts);

}

#endif // SECURITY_ENGINE_H
