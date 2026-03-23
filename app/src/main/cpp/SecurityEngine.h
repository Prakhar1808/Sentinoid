#pragma once

#include "ISecurityCore.h"

class SecurityEngine : public ISecurityCore {
public:
    static SecurityEngine& getInstance();

    bool detectThreat(const std::string& processData) override;
    void triggerStasisMode() override;
    std::string deriveVaultKey(const std::string& seed) override;

private:
    SecurityEngine() = default;
    SecurityEngine(const SecurityEngine&) = delete;
    void operator=(const SecurityEngine&) = delete;
};