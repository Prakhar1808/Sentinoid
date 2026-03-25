#ifndef LLM_CLIENT_H
#define LLM_CLIENT_H

#include <string>
#include <vector>
#include <optional>
#include <memory>

namespace sentinoid {

enum class HardwareTier {
    TIER_RTX_HIGH,      // RTX 5060+ (7B models)
    TIER_AMD_GPU,      // AMD ROCm (7B models)
    TIER_RTX_OLD,      // Older NVIDIA (4B models)
    TIER_IGPU,         // Integrated graphics (3B models)
    TIER_CPU           // CPU only (1.5-2B models)
};

struct ModelConfig {
    std::string name;
    std::string ollama_name;
    int max_tokens;
    float temperature;
    HardwareTier tier;
};

struct LlmResponse {
    std::string text;
    int threat_level;
    std::vector<std::string> vectors;
    std::string mitigation;
    bool success;
    std::string error;
};

class LlmClient {
public:
    LlmClient();
    ~LlmClient();

    bool init();
    bool isAvailable() const;
    
    HardwareTier detectHardware();
    bool selectModel(HardwareTier tier);
    bool selectModelByName(const std::string& model_name);
    
    LlmResponse analyzeLog(const std::string& log_line);
    LlmResponse analyzeLogs(const std::vector<std::string>& logs);
    
    std::vector<ModelConfig> getAvailableModels() const;
    ModelConfig getCurrentModel() const;

private:
    bool checkOllamaConnection();
    bool pullModelIfNeeded(const std::string& model_name);
    LlmResponse parseSecurityResponse(const std::string& response);
    
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

std::string hardwareTierToString(HardwareTier tier);
std::string getSystemPrompt();

}

#endif // LLM_CLIENT_H
