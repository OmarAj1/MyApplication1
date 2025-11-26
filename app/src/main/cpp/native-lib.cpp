#include <jni.h>
#include <string>

// Corrected the function signature to match the Java package path
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_UserMainActivity_getNativeVersionFromJNI(JNIEnv* env, jobject /* this */) {
    std::string version = "NEXUS-CORE-v1.2a";
    return env->NewStringUTF(version.c_str());
}