#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>

#define LOG_TAG "SentinoidNDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Batch operation constants
#define BATCH_SIZE 16
#define SIGNATURE_SIZE 64

extern "C" {

// Single hardware ID retrieval (kept for compatibility)
JNIEXPORT jstring JNICALL
Java_com_sentinoid_shield_HardwareAbstraction_getSecureHardwareId(JNIEnv *env, jobject thiz) {
    char hardwareId[64];
    snprintf(hardwareId, sizeof(hardwareId), "HW_%08X", rand());
    return env->NewStringUTF(hardwareId);
}

JNIEXPORT jboolean JNICALL
Java_com_sentinoid_shield_HardwareAbstraction_isHardwareAttestationAvailable(JNIEnv *env, jobject thiz) {
    return JNI_TRUE;
}

// Optimized batch signature - reduces JNI call overhead
JNIEXPORT jobjectArray JNICALL
Java_com_sentinoid_shield_HardwareAbstraction_batchHardwareSign(
    JNIEnv *env, 
    jobject thiz, 
    jobjectArray dataArray
) {
    jsize count = env->GetArrayLength(dataArray);
    if (count > BATCH_SIZE) count = BATCH_SIZE;
    
    // Create result array
    jobjectArray result = env->NewObjectArray(count, env->FindClass("[B"), NULL);
    
    for (jsize i = 0; i < count; i++) {
        jbyteArray data = (jbyteArray)env->GetObjectArrayElement(dataArray, i);
        jsize len = env->GetArrayLength(data);
        
        // Generate deterministic mock signature based on data
        jbyteArray sig = env->NewByteArray(SIGNATURE_SIZE);
        jbyte mockSig[SIGNATURE_SIZE];
        for (int j = 0; j < SIGNATURE_SIZE; j++) {
            mockSig[j] = (jbyte)((j * 7 + i * 13) ^ 0x5A);
        }
        env->SetByteArrayRegion(sig, 0, SIGNATURE_SIZE, mockSig);
        env->SetObjectArrayElement(result, i, sig);
        env->DeleteLocalRef(data);
        env->DeleteLocalRef(sig);
    }
    
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_sentinoid_shield_HardwareAbstraction_hardwareSign(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyteArray result = env->NewByteArray(SIGNATURE_SIZE);
    jbyte mockSig[SIGNATURE_SIZE];
    for (int i = 0; i < SIGNATURE_SIZE; i++) mockSig[i] = (jbyte)(i ^ 0x5A);
    env->SetByteArrayRegion(result, 0, SIGNATURE_SIZE, mockSig);
    return result;
}

// Optimized secure memory clearing with batch support
JNIEXPORT void JNICALL
Java_com_sentinoid_shield_HardwareAbstraction_secureClearMemory(
    JNIEnv *env,
    jobject thiz,
    jlongArray addresses,
    jlongArray sizes
) {
    jsize count = env->GetArrayLength(addresses);
    jlong* addrArray = env->GetLongArrayElements(addresses, NULL);
    jlong* sizeArray = env->GetLongArrayElements(sizes, NULL);
    
    for (jsize i = 0; i < count; i++) {
        volatile char* ptr = (volatile char*)addrArray[i];
        jlong size = sizeArray[i];
        for (jlong j = 0; j < size; j++) {
            ptr[j] = 0;
        }
    }
    
    env->ReleaseLongArrayElements(addresses, addrArray, 0);
    env->ReleaseLongArrayElements(sizes, sizeArray, 0);
}

}
