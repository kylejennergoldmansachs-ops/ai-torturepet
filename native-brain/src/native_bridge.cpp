#include <jni.h>
#include <string>
#include "reservoir.h"

static brain::Reservoir g_reservoir;
static brain::Config g_config;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_aitorture_NativeBrain_nativeInit(JNIEnv *env, jclass cls, jint neuronCount, jint fanout) {
    g_config.neuron_count = static_cast<uint32_t>(neuronCount);
    g_config.fanout = static_cast<uint32_t>(fanout);
    bool ok = g_reservoir.init(g_config, 1337u);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aitorture_NativeBrain_nativeApplyInputs(JNIEnv *env, jclass cls, jfloatArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::vector<float> vec(len);
    env->GetFloatArrayRegion(arr, 0, len, vec.data());
    g_reservoir.apply_inputs(vec);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_aitorture_NativeBrain_nativeStep(JNIEnv *env, jclass cls) {
    g_reservoir.step();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aitorture_NativeBrain_nativeExportSummary(JNIEnv *env, jclass cls) {
    std::string s = g_reservoir.export_state_summary();
    return env->NewStringUTF(s.c_str());
}
