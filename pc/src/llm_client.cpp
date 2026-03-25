#include "llm_client.hpp"
#include <iostream>
#include <sstream>
#include <curl/curl.h>
#include <json/json.h>
#include <thread>
#include <fstream>
#include <regex>
#include <cstdlib>

namespace sentinoid {

static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    ((std::string*)userp)->append((char*)contents, size * nmemb);
    return size * nmemb;
}

struct ModelConfig model_configs[] = {
    {"RTX High", "qwen2.5:7b", 512, 0.1f, HardwareTier::TIER_RTX_HIGH},
    {"AMD GPU", "qwen2.5:7b", 512, 0.1f, HardwareTier::TIER_AMD_GPU},
    {"RTX Old", "qwen2.5:4b", 512, 0.1f, HardwareTier::TIER_RTX_OLD},
    {"iGPU", "llama3.2:3b", 512, 0.1f, HardwareTier::TIER_IGPU},
    {"CPU", "qwen2.5:1.8b", 512, 0.1f, HardwareTier::TIER_CPU}
};

class LlmClient::Impl {
public:
    CURL* curl;
    std::string ollama_host;
    ModelConfig current_model;
    HardwareTier current_tier;
    bool available;
    bool model_loaded;

    Impl() : curl(nullptr), current_tier(HardwareTier::TIER_CPU), available(false), model_loaded(false) {
        ollama_host = "http://localhost:11434";
    }

    ~Impl() {
        if (curl) {
            curl_easy_cleanup(curl);
        }
    }

    bool checkConnection() {
        curl = curl_easy_init();
        if (!curl) return false;

        std::string response;
        curl_easy_setopt(curl, CURLOPT_URL, (ollama_host + "/api/tags").c_str());
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);

        CURLcode res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
        curl = nullptr;

        if (res == CURLE_OK) {
            available = response.find("models") != std::string::npos;
            return available;
        }
        return false;
    }

    HardwareTier detectGpu() {
        FILE* pipe = popen("nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null", "r");
        if (pipe) {
            char buffer[256];
            if (fgets(buffer, sizeof(buffer), pipe)) {
                std::string gpu_name(buffer);
                gpu_name = gpu_name.substr(0, gpu_name.find('\n'));
                
                if (gpu_name.find("RTX 50") != std::string::npos ||
                    gpu_name.find("RTX 40") != std::string::npos ||
                    gpu_name.find("RTX 30") != std::string::npos) {
                    pclose(pipe);
                    return HardwareTier::TIER_RTX_HIGH;
                }
                return HardwareTier::TIER_RTX_OLD;
            }
            pclose(pipe);
        }

        pipe = popen("rocminfo 2>/dev/null | grep -i 'gfx' | head -1", "r");
        if (pipe) {
            char buffer[256];
            if (fgets(buffer, sizeof(buffer), pipe)) {
                pclose(pipe);
                return HardwareTier::TIER_AMD_GPU;
            }
            pclose(pipe);
        }

        return HardwareTier::TIER_CPU;
    }

    bool pullModel(const std::string& model_name) {
        curl = curl_easy_init();
        if (!curl) return false;

        Json::Value json;
        json["name"] = model_name;

        Json::StreamWriterBuilder builder;
        std::string json_str = Json::writeString(builder, json);

        std::string url = ollama_host + "/api/pull";
        std::string response;

        struct curl_slist* headers = nullptr;
        headers = curl_slist_append(headers, "Content-Type: application/json");

        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_POST, 1);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_str.c_str());
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 300L);

        CURLcode res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
        curl = nullptr;
        curl_slist_free_all(headers);

        return res == CURLE_OK;
    }

    LlmResponse generate(const std::string& prompt) {
        LlmResponse result;
        result.success = false;

        curl = curl_easy_init();
        if (!curl) {
            result.error = "Failed to initialize CURL";
            return result;
        }

        Json::Value json;
        json["model"] = current_model.ollama_name;
        json["prompt"] = prompt;
        json["stream"] = false;
        json["options"]["temperature"] = current_model.temperature;
        json["options"]["num_predict"] = current_model.max_tokens;

        Json::StreamWriterBuilder builder;
        std::string json_str = Json::writeString(builder, json);

        std::string url = ollama_host + "/api/generate";
        std::string response;

        struct curl_slist* headers = nullptr;
        headers = curl_slist_append(headers, "Content-Type: application/json");

        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_POST, 1);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_str.c_str());
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);

        CURLcode res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);
        curl = nullptr;
        curl_slist_free_all(headers);

        if (res != CURLE_OK) {
            result.error = "CURL error: " + std::string(curl_easy_strerror(res));
            return result;
        }

        result = parseResponse(response);
        return result;
    }

    LlmResponse parseResponse(const std::string& json_str) {
        LlmResponse result;
        result.success = false;

        try {
            Json::CharReaderBuilder builder;
            std::istringstream stream(json_str);
            Json::Value root;
            std::string errs;

            if (Json::parseFromStream(builder, stream, &root, &errs)) {
                if (root.isMember("response")) {
                    result.text = root["response"].asString();
                    result = parseSecurityResponse(result.text);
                    result.success = true;
                } else if (root.isMember("error")) {
                    result.error = root["error"].asString();
                }
            }
        } catch (const std::exception& e) {
            result.error = std::string("JSON parse error: ") + e.what();
        }

        return result;
    }

    LlmResponse parseSecurityResponse(const std::string& text) {
        LlmResponse result;
        result.text = text;
        result.threat_level = 0;
        result.success = true;

        std::regex level_regex("\"threat_level\"\\s*:\\s*(\\d+)");
        std::smatch match;
        if (std::regex_search(text, match, level_regex)) {
            result.threat_level = std::stoi(match[1]);
        }

        return result;
    }
};

LlmClient::LlmClient() : pImpl(std::make_unique<Impl>()) {}

LlmClient::~LlmClient() = default;

bool LlmClient::init() {
    curl_global_init(CURL_GLOBAL_DEFAULT);
    return pImpl->checkConnection();
}

bool LlmClient::isAvailable() const {
    return pImpl->available;
}

HardwareTier LlmClient::detectHardware() {
    HardwareTier tier = pImpl->detectGpu();
    pImpl->current_tier = tier;
    return tier;
}

bool LlmClient::selectModel(HardwareTier tier) {
    pImpl->current_tier = tier;
    
    for (const auto& config : model_configs) {
        if (config.tier == tier) {
            pImpl->current_model = config;
            return true;
        }
    }
    return false;
}

bool LlmClient::selectModelByName(const std::string& model_name) {
    for (const auto& config : model_configs) {
        if (config.ollama_name == model_name) {
            pImpl->current_model = config;
            pImpl->current_tier = config.tier;
            return true;
        }
    }
    return false;
}

LlmResponse LlmClient::analyzeLog(const std::string& log_line) {
    std::string prompt = getSystemPrompt() + "\n\nLog: " + log_line + "\n\nResponse (JSON only):";
    return pImpl->generate(prompt);
}

LlmResponse LlmClient::analyzeLogs(const std::vector<std::string>& logs) {
    std::string combined;
    for (size_t i = 0; i < logs.size() && i < 10; ++i) {
        combined += logs[i] + "\n";
    }
    return analyzeLog(combined);
}

std::vector<ModelConfig> LlmClient::getAvailableModels() const {
    std::vector<ModelConfig> configs;
    for (const auto& config : model_configs) {
        configs.push_back(config);
    }
    return configs;
}

ModelConfig LlmClient::getCurrentModel() const {
    return pImpl->current_model;
}

bool LlmClient::checkOllamaConnection() {
    return pImpl->checkConnection();
}

bool LlmClient::pullModelIfNeeded(const std::string& model_name) {
    return pImpl->pullModel(model_name);
}

LlmResponse LlmClient::parseSecurityResponse(const std::string& response) {
    return pImpl->parseSecurityResponse(response);
}

std::string hardwareTierToString(HardwareTier tier) {
    switch (tier) {
        case HardwareTier::TIER_RTX_HIGH: return "RTX 5060+ (High Performance)";
        case HardwareTier::TIER_AMD_GPU: return "AMD GPU (ROCm)";
        case HardwareTier::TIER_RTX_OLD: return "Older NVIDIA GPU";
        case HardwareTier::TIER_IGPU: return "Integrated Graphics";
        case HardwareTier::TIER_CPU: return "CPU Only";
        default: return "Unknown";
    }
}

std::string getSystemPrompt() {
    return R"(Role: Air-Gapped Security Analyst
Task: Analyze Android logs for IOCTL anomalies, USB AOA exploits, and privilege escalation patterns.
Constraint: Output ONLY valid JSON. No prose. No markdown.
Schema: {"threat_level": 0-10, "vectors": [], "mitigation": ""}
Temperature: 0.1)";
}

}
