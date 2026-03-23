#ifndef ISECURITYCORE_H
#define ISECURITYCORE_H

#include <string>

class ISecurityCore {
public:
    virtual ~ISecurityCore() {}
    virtual bool detectThreat(const std::string& processData) = 0;
    virtual void triggerStasisMode() = 0;
    virtual std::string deriveVaultKey(const std::string& seed) = 0;
};

#endif