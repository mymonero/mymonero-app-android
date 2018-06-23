#include <jni.h>
#include <string>
#include <sstream>
//
//#include <boost/atomic.hpp>
#include <android/log.h>
#include "AppServiceLocator.hpp"
//
// Lifecycle - Imperatives - Init
extern "C" JNIEXPORT jint JNI_OnLoad(
        JavaVM* vm, void* reserved
) {
    __android_log_print(ANDROID_LOG_INFO,  __FUNCTION__, "~~~> JNI OnLoad called");
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    //
    // TODO: set logging functions w/ android log here or in initLib
    //
    return JNI_VERSION_1_6; // would be nice not to have to return a constant here..
}
extern "C" JNIEXPORT void JNICALL Java_com_mymonero_Application_AppLib_1Bridge_initLib(
        JNIEnv *env,
        jobject pThis,
        jstring documentsPath_jstring
) {
    jboolean isCopy = JNI_TRUE;
    const char *documentsPathBytes = env->GetStringUTFChars(documentsPath_jstring, &isCopy);
    App::ServiceLocator::instance().documentsPath = std::move(std::string(documentsPathBytes));
    env->ReleaseStringUTFChars(documentsPath_jstring, documentsPathBytes);
    //
    //
    // TODO: set logging functions here or in init w/ android log
}
//
// Runtime - Accessors
extern "C" JNICALL jstring JNICALL
Java_com_mymonero_Application_AppLib_1Bridge_stringFromJNI(
    JNIEnv *env,
    jobject pThis
) {
    std::stringstream stream;
    stream << "Hello from C++ with documentsPath" << App::ServiceLocator::instance().documentsPath;
    //
    return env->NewStringUTF(stream.str().c_str());
}


//// Copy the temporary C string into its dynamically allocated
//// final location. Then releases the temporary string.
//jsize stringLength = pEnv->GetStringUTFLength(pString);
//entry->mValue.mString = new char[stringLength + 1];
//// Directly copies the Java String into our new C buffer.
//pEnv->GetStringUTFRegion(pString, 0, stringLength, entry->mValue.mString);
//// Append the null character for string termination.
//entry->mValue.mString[stringLength] = '\0';


//jclass clazz = env->FindClass("com/ihorkucherenko/storage/Store");
//jmethodID constructor = env->GetMethodID(clazz, "<init>", "()V");
//jobject storeObject = env->NewObject(clazz, constructor);
// ^---- just an example of init


//jclass clazz = pEnv->FindClass("com/ihorkucherenko/storage/Store");
//jmethodID methodId = pEnv->GetMethodID(clazz, "onIntegerSet", "(Ljava/lang/String;I)V");
//pEnv->CallVoidMethod(pThis, methodId, pKey, pInteger);


//jfieldID fieldId = env->GetFieldID(clazz, "property", "I");
//jint value = env->GetIntField(storeObject, fieldId);


//void throwNoKeyException(JNIEnv* pEnv) {
//    jclass clazz = pEnv->FindClass("java/lang/IllegalArgumentException");
//    if (clazz != NULL) {
//        pEnv->ThrowNew(clazz, "Key does not exist.");
//    }
//    pEnv->DeleteLocalRef(clazz);
//}

// Kotlin:
// public class FooModel {
//    String fooString;
// }
// -
//FooModel fooModel = new FooModel();
//passingJavaObject(fooModel);
// -
//public native void passingJavaObject(FooModel fooModel);
//
// C++:
//Java_com_foo_sample_FooActivity_passingJavaObject(JNIEnv *env, jobject instance,
//        jobject fooModel_) {
//
//jclass fooModel = (*env)->GetObjectClass(env, fooModel_);
//jfieldID fooFloat = (*env)->GetFieldID(env, fooModel, "fooFloat", "D");
//jfieldID fooLong = (*env)->GetFieldID(env, fooModel, "fooLong", "J");
//jfieldID fooInt = (*env)->GetFieldID(env, fooModel, "fooInt", "I");
//jfieldID fooString = (*env)->GetFieldID(env, fooModel, "fooString", "Ljava/lang/String;");
//
//(*env)->SetFloatField(env, fooModel_ , fooFloat, 1.1f);
//(*env)->SetLongField(env, fooModel_ , fooLong, 1);
//(*env)->SetIntField(env, fooModel_ , fooInt, 1);
//(*env)->SetObjectField(env, fooModel_ , fooString, (*env)->NewStringUTF(env, "foo"));
//}
// OR
//JNIEXPORT jobject JNICALL
//Java_com_foo_sample_FooActivity_passingJavaObject(JNIEnv *env, jobject instance) {
//
//    jclass fooModelClass = (*env)->FindClass(env, "com/foo/sample/FooModel");
//    jmethodID methodId = (*env)->GetMethodID(env, fooModelClass, "<init>", "()V");
//    jobject fooModel = (*env)->NewObject(env, cls, methodId);
//
//    //getters and setters
//    return fooModel;
//}
//http://vorm.io/android-ndk-passing-complex-data-not-scary-anymore/
