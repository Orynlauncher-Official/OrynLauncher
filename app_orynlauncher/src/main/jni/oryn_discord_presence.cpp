#define DISCORDPP_IMPLEMENTATION
#include <jni.h>
#include <android/log.h>
#include <discordpp.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <ctime>

namespace {
constexpr uint64_t kApplicationId = 1556187212620763238ULL;
constexpr const char* kTag = "OrynDiscordPresence";
std::mutex gMutex;
std::shared_ptr<discordpp::Client> gClient;
std::atomic<bool> gRunning{false};
std::thread gCallbacks;

void logWarning(const char* message) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "%s", message);
}

void update(const std::string& details, const std::string& state) {
    std::shared_ptr<discordpp::Client> client;
    { std::lock_guard<std::mutex> lock(gMutex); client = gClient; }
    if (!client) return;
    discordpp::Activity activity{};
    // Discord otherwise uses the detected game/application name (often just Minecraft).
    // Explicitly brand both launcher and in-game presence as OrynLauncher Plus.
    activity.SetName("OrynLauncher Plus");
    activity.SetType(discordpp::ActivityTypes::Playing);
    // Show the dynamic details text ("Browsing..." / "Playing...") as the
    // activity status instead of only showing the registered game name.
    activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::Details);
    activity.SetDetails(details);
    if (!state.empty()) activity.SetState(state);
    // Show elapsed time while the user is playing OrynLauncherPlus.
    if (details == "Playing OrynLauncherPlus") {
        discordpp::ActivityTimestamps timestamps;
        timestamps.SetStart(static_cast<uint64_t>(std::time(nullptr)));
        activity.SetTimestamps(timestamps);
    }
    client->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
        if (!result.Successful()) logWarning("Rich Presence update failed.");
    });
}
}

extern "C" JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_OrynDiscordPresence_nativeStart(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gClient) return;
    try {
        gClient = std::make_shared<discordpp::Client>();
        gClient->SetApplicationId(kApplicationId);
        gRunning.store(true);
        gCallbacks = std::thread([] {
            while (gRunning.load()) {
                discordpp::RunCallbacks();
                std::this_thread::sleep_for(std::chrono::milliseconds(50));
            }
        });
    } catch (...) {
        gRunning.store(false);
        gClient.reset();
        logWarning("SDK initialization failed.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_OrynDiscordPresence_nativeUpdate(
        JNIEnv* env, jclass, jstring details, jstring state) {
    if (!details || !state) return;
    const char* d = env->GetStringUTFChars(details, nullptr);
    const char* s = env->GetStringUTFChars(state, nullptr);
    if (d && s) update(d, s);
    if (d) env->ReleaseStringUTFChars(details, d);
    if (s) env->ReleaseStringUTFChars(state, s);
}

extern "C" JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_OrynDiscordPresence_nativeStop(JNIEnv*, jclass) {
    std::shared_ptr<discordpp::Client> client;
    { std::lock_guard<std::mutex> lock(gMutex); client = gClient; }

    // Stop the background callback loop first, then explicitly flush the clear
    // operation while the SDK client is still alive. Otherwise the process can
    // exit before Discord receives the clear and the old activity may linger.
    gRunning.store(false);
    if (gCallbacks.joinable() && gCallbacks.get_id() != std::this_thread::get_id()) {
        gCallbacks.join();
    }
    if (client) {
        client->ClearRichPresence();
        for (int i = 0; i < 4; ++i) {
            discordpp::RunCallbacks();
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
    }
    { std::lock_guard<std::mutex> lock(gMutex); gClient.reset(); }
}
