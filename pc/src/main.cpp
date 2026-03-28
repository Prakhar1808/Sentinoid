#include "security_engine.hpp"
#include "llm_client.hpp"
#include "aoa_bridge.hpp"
#include <iostream>
#include <csignal>
#include <atomic>
#include <thread>
#include <chrono>

namespace sentinoid {
    std::atomic<bool> g_running{true};
}

void signalHandler(int signal) {
    sentinoid::g_running = false;
}

void printBanner() {
    std::cout << R"(
   ___ _                    _             _   ___ ___ 
  / __| |___ ___ _ _  ___  | |_ ___ ___  | | / __| __|
 | (_ | / -_) _ | ' \(_-<  |  _/ -_) _ \ | | \__ \ _| 
  \___|_\___\___|_||_/__/   \__\___\___/ |_| |___/___|
                                                     
   Sentinoid PC Security Engine - Air-Gapped Defense
)" << std::endl;
}

void printUsage(const char* program) {
    std::cout << "Usage: " << program << " [options]\n"
              << "Options:\n"
              << "  -h, --help     Show this help\n"
              << "  -m, --model    Specify model (e.g., qwen2.5:7b)\n"
              << "  -t, --tier     Hardware tier (rtx-high, amd, rtx-old, igpu, cpu)\n"
              << "  --no-llm      Run without LLM (log capture only)\n"
              << "  --test        Run in test mode (generate sample analysis)\n"
              << std::endl;
}

int main(int argc, char* argv[]) {
    printBanner();

    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    bool no_llm = false;
    bool test_mode = false;
    std::string model_override;
    std::string tier_override;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "-h" || arg == "--help") {
            printUsage(argv[0]);
            return 0;
        } else if (arg == "--no-llm") {
            no_llm = true;
        } else if (arg == "--test") {
            test_mode = true;
        } else if ((arg == "-m" || arg == "--model") && i + 1 < argc) {
            model_override = argv[++i];
        } else if ((arg == "-t" || arg == "--tier") && i + 1 < argc) {
            tier_override = argv[++i];
        }
    }

    sentinoid::SecurityEngine engine;

    if (!engine.init()) {
        std::cerr << "Failed to initialize security engine" << std::endl;
        return 1;
    }

    auto& llm = engine.getLlmClient();
    auto& aoa = engine.getAoaBridge();

    if (!no_llm && llm.isAvailable()) {
        if (!model_override.empty()) {
            llm.selectModelByName(model_override);
        } else if (!tier_override.empty()) {
            sentinoid::HardwareTier tier;
            if (tier_override == "rtx-high") tier = sentinoid::HardwareTier::TIER_RTX_HIGH;
            else if (tier_override == "amd") tier = sentinoid::HardwareTier::TIER_AMD_GPU;
            else if (tier_override == "rtx-old") tier = sentinoid::HardwareTier::TIER_RTX_OLD;
            else if (tier_override == "igpu") tier = sentinoid::HardwareTier::TIER_IGPU;
            else if (tier_override == "cpu") tier = sentinoid::HardwareTier::TIER_CPU;
            llm.selectModel(tier);
        }

        auto model = llm.getCurrentModel();
        std::cout << "Using model: " << model.ollama_name 
                  << " (Tier: " << sentinoid::hardwareTierToString(model.tier) << ")\n";
    } else if (no_llm) {
        std::cout << "Running in passive mode (no LLM)\n";
    } else {
        std::cout << "Warning: Ollama not available. Install and run 'ollama serve'\n";
    }

    engine.setAlertCallback([](const sentinoid::SecurityAlert& alert) {
        std::cout << "\n[ALERT] Threat Level: " << alert.score << "/10\n";
        std::cout << "Vectors: ";
        for (const auto& v : alert.attack_vectors) {
            std::cout << v << " ";
        }
        std::cout << "\nMitigation: " << alert.mitigation << "\n";
    });

    engine.setLogAnalysisCallback([](const std::string& log, const sentinoid::LlmResponse& resp) {
        if (resp.threat_level > 0) {
            std::cout << "[ANALYSIS] Threat: " << resp.threat_level << "/10\n";
        }
    });

    if (test_mode) {
        std::cout << "\n=== Test Mode ===\n";
        std::vector<std::string> test_logs = {
            "[AUTH] Failed root access attempt from /system/app/Superuser.apk",
            "[USB] IOCTL request 0x5401 from unknown device (VID:0x0000 PID:0x0000)",
            "[SELINUX] avc: denied { read } for path=/data/local/tmp/su",
            "[KERNEL] suspicious syscall: setuid(0) from uid=1000"
        };

        for (const auto& log : test_logs) {
            auto resp = llm.analyzeLog(log);
            std::cout << "Log: " << log << "\n";
            std::cout << "Response: " << resp.text << "\n\n";
        }
        return 0;
    }

    engine.start();

    std::cout << "\n=== Sentinoid Active ===\n";
    std::cout << "Waiting for Android device connection via USB...\n";
    std::cout << "Press Ctrl+C to stop\n\n";

    while (sentinoid::g_running) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
        
        if (engine.getAlertCount() > 0) {
            std::cout << "Total alerts: " << engine.getAlertCount() << "\r";
            std::cout.flush();
        }
    }

    engine.stop();
    std::cout << "\nSentinoid shutdown complete.\n";

    return 0;
}
