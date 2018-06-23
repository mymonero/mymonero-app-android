# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# boost
set(boost_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs/boost)
include_directories(${boost_DIR}/include)
# boost - static libs
add_library(boost_atomic SHARED IMPORTED)
set_target_properties(
    boost_atomic PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_atomic-gcc-mt-1_53.a
)
add_library(boost_chrono SHARED IMPORTED)
set_target_properties(
    boost_chrono PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_chrono-gcc-mt-1_53.a
)
add_library(boost_context SHARED IMPORTED)
set_target_properties(
    boost_context PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_context-gcc-mt-1_53.a
)
add_library(boost_date_time SHARED IMPORTED)
set_target_properties(
    boost_date_time PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_date_time-gcc-mt-1_53.a
)
add_library(boost_exception SHARED IMPORTED)
set_target_properties(
    boost_exception PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_exception-gcc-mt-1_53.a
)
add_library(boost_filesystem SHARED IMPORTED)
set_target_properties(
    boost_filesystem PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_filesystem-gcc-mt-1_53.a
)
add_library(boost_graph SHARED IMPORTED)
set_target_properties(
    boost_graph PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_graph-gcc-mt-1_53.a
)
add_library(boost_iostreams SHARED IMPORTED)
set_target_properties(
    boost_iostreams PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_iostreams-gcc-mt-1_53.a
)
add_library(boost_math_c99 SHARED IMPORTED)
set_target_properties(
    boost_math_c99 PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_c99-gcc-mt-1_53.a
)
add_library(boost_math_c99f SHARED IMPORTED)
set_target_properties(
    boost_math_c99f PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_c99f-gcc-mt-1_53.a
)
add_library(boost_math_c99l SHARED IMPORTED)
set_target_properties(
    boost_math_c99l PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_c99l-gcc-mt-1_53.a
)
add_library(boost_math_tr1 SHARED IMPORTED)
set_target_properties(
    boost_math_tr1 PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_tr1-gcc-mt-1_53.a
)
add_library(boost_math_tr1f SHARED IMPORTED)
set_target_properties(
    boost_math_tr1f PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_tr1f-gcc-mt-1_53.a
)
add_library(boost_math_tr1l SHARED IMPORTED)
set_target_properties(
    boost_math_tr1l PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_math_tr1l-gcc-mt-1_53.a
)
add_library(boost_prg_exec_monitor SHARED IMPORTED)
set_target_properties(
    boost_prg_exec_monitor PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_prg_exec_monitor-gcc-mt-1_53.a
)
add_library(boost_program_options SHARED IMPORTED)
set_target_properties(
    boost_program_options PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_program_options-gcc-mt-1_53.a
)
add_library(boost_random SHARED IMPORTED)
set_target_properties(
    boost_random PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_random-gcc-mt-1_53.a
)
add_library(boost_regex SHARED IMPORTED)
set_target_properties(
    boost_regex PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_regex-gcc-mt-1_53.a
)
add_library(boost_serialization SHARED IMPORTED)
set_target_properties(
    boost_serialization PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_serialization-gcc-mt-1_53.a
)
add_library(boost_signals SHARED IMPORTED)
set_target_properties(
    boost_signals PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_signals-gcc-mt-1_53.a
)
add_library(boost_system SHARED IMPORTED)
set_target_properties(
    boost_system PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_system-gcc-mt-1_53.a
)
add_library(boost_test_exec_monitor SHARED IMPORTED)
set_target_properties(
    boost_test_exec_monitor PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_test_exec_monitor-gcc-mt-1_53.a
)
add_library(boost_thread SHARED IMPORTED)
set_target_properties(
    boost_thread PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_thread-gcc-mt-1_53.a
)
add_library(boost_timer SHARED IMPORTED)
set_target_properties(
    boost_timer PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_timer-gcc-mt-1_53.a
)
add_library(boost_unit_test_framework SHARED IMPORTED)
set_target_properties(
    boost_unit_test_framework PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_unit_test_framework-gcc-mt-1_53.a
)
add_library(boost_wave SHARED IMPORTED)
set_target_properties(
    boost_wave PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_wave-gcc-mt-1_53.a
)
add_library(boost_wserialization SHARED IMPORTED)
set_target_properties(
    boost_wserialization PROPERTIES IMPORTED_LOCATION
    ${boost_DIR}/${ANDROID_ABI}/lib/libboost_wserialization-gcc-mt-1_53.a
)

# app lib
set(mymonero_app_lib_DIR ${CMAKE_SOURCE_DIR}/src/main/cpp/mymonero_app_lib)
set(mymonero_app_lib_SOURCES
    ${mymonero_app_lib_DIR}/AppServiceLocator.cpp
    ${mymonero_app_lib_DIR}/document_persister.cpp
    ${mymonero_app_lib_DIR}/PasswordController.cpp
)
add_library(mymonero_app_lib SHARED ${mymonero_app_lib_SOURCES})
include_directories(${mymonero_app_lib_DIR})

# app JNI bridge code
set(mymonero_app_jni_bridge_DIR src/main/cpp/mymonero_app_jni_bridge)
include_directories(${mymonero_app_jni_bridge_DIR})
add_library(mymonero_app_jni_bridge SHARED ${mymonero_app_jni_bridge_DIR}/index.cpp)

# ndk support
find_library(log-lib log)

# final link
target_link_libraries(
    mymonero_app_jni_bridge
    #
    mymonero_app_lib
    ${log-lib}
)
target_link_libraries(
    mymonero_app_lib
    #
    boost_atomic
    boost_chrono
    boost_context
    boost_date_time
    boost_exception
    boost_filesystem
    boost_graph
    boost_iostreams
    boost_math_c99
    boost_math_c99f
    boost_math_c99l
    boost_math_tr1
    boost_math_tr1f
    boost_math_tr1l
    boost_prg_exec_monitor
    boost_program_options
    boost_random
    boost_regex
    boost_serialization
    boost_signals
    boost_system
    boost_test_exec_monitor
    boost_thread
    boost_timer
    boost_unit_test_framework
    boost_wave
    boost_wserialization
    ${log-lib}
)