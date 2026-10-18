#define DISCORDPP_IMPLEMENTATION
#include <discordpp.h>

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

namespace {
constexpr uint64_t kOrynDiscordApplicationId = 1561214038786969620ULL;
constexpr const char* kLogTag = "OrynDiscordPresence";

std::mutex g_mutex;
std::unique_ptr<discordpp::Client> g_client;
std::thread g_callbackThread;
std::atomic<bool> g_running{false};

std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void updatePresence(const std::string& details, const std::string& state) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_client) return;

    discordpp::Activity activity{};
    activity.SetDetails(details);
    activity.SetState(state);
    g_client->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        if (!result.Successful()) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                "Discord presence update was not accepted by the SDK.");
        }
    });
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_OrynDiscordPresence_nativeStart(
        JNIEnv* env, jclass, jstring details, jstring state) {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_client) {
            g_client = std::make_unique<discordpp::Client>();
            g_client->SetApplicationId(kOrynDiscordApplicationId);
            g_client->AddLogCallback([](std::string message, discordpp::LoggingSeverity) {
                __android_log_print(ANDROID_LOG_DEBUG, kLogTag, "%s", message.c_str());
            }, discordpp::LoggingSeverity::Warning);
        }
    }

    if (!g_running.exchange(true)) {
        g_callbackThread = std::thread([] {
            while (g_running.load()) {
                discordpp::RunCallbacks();
                std::this_thread::sleep_for(std::chrono::milliseconds(16));
            }
        });
        g_callbackThread.detach();
    }

    updatePresence(fromJava(env, details), fromJava(env, state));
}

extern "C" JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_OrynDiscordPresence_nativeUpdate(
        JNIEnv* env, jclass, jstring details, jstring state) {
    updatePresence(fromJava(env, details), fromJava(env, state));
}
