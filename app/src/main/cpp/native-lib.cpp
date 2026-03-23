#include <jni.h>
#include <string>
#include "SecurityEngine.h"

// Hardware Status Hook
extern "C" JNIEXPORT jstring JNICALL
Java_com_sentinoid_shield_SecurityModule_getHardwareStatus(JNIEnv* env, jobject) {
    std::string status = "CORE_ACTIVE_" + detectSystemHardware(); 
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sentinoid_shield_SecurityModule_nativeDetectThreat(JNIEnv *env, jobject /* this */, jstring data) {
    const char *nativeString = env->GetStringUTFChars(data, 0);

    // Logic to call your SecurityEngine
    bool isThreat = SecurityEngine::getInstance().detectThreat(nativeString);

    env->ReleaseStringUTFChars(data, nativeString);
    return isThreat ? JNI_TRUE : JNI_FALSE;
}

// A test function to confirm the JNI bridge is working
extern "C" JNIEXPORT jstring JNICALL
Java_com_sentinoid_shield_SecurityModule_stringFromJNI(JNIEnv *env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}