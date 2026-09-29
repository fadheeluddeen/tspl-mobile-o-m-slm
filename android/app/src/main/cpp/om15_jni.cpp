#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"
#include "common.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "OM15_LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static mtmd_context * g_mtmd_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_technavious_om15_ai_LlamaEngine_loadModel(
    JNIEnv *env, jobject /* this */,
    jstring modelPath, jstring mmProjPath) {

    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char *mmproj_path = env->GetStringUTFChars(mmProjPath, nullptr);

    LOGI("Loading model: %s", model_path);
    LOGI("Loading mmproj: %s", mmproj_path);

    auto to_logcat = [](ggml_log_level level, const char * text, void *) {
        if (level >= GGML_LOG_LEVEL_INFO) __android_log_write(level >= GGML_LOG_LEVEL_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO, "llama.cpp", text);
    };
    llama_log_set(to_logcat, nullptr);
    mtmd_helper_log_set(to_logcat, nullptr);
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(model_path, model_params);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmProjPath, mmproj_path);
        return JNI_FALSE;
    }

    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.use_gpu = false;
    mtmd_params.n_threads = 4;
    // CPU flash-attention path segfaults on this device
    mtmd_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;

    g_mtmd_ctx = mtmd_init_from_file(mmproj_path, g_model, mtmd_params);
    if (!g_mtmd_ctx) {
        LOGE("Failed to load mmproj");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmProjPath, mmproj_path);
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(mmProjPath, mmproj_path);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_technavious_om15_ai_LlamaEngine_runInference(
    JNIEnv *env, jobject /* this */,
    jstring imagePath, jstring prompt) {

    if (!g_model || !g_mtmd_ctx) {
        return env->NewStringUTF("{\"error\":\"model not loaded\"}");
    }

    const char *image_path = env->GetStringUTFChars(imagePath, nullptr);
    const char *prompt_text = env->GetStringUTFChars(prompt, nullptr);

    LOGI("Running inference on: %s", image_path);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;

    llama_context * ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(prompt, prompt_text);
        return env->NewStringUTF("{\"error\":\"context creation failed\"}");
    }

    // Load image using helper
    mtmd_helper_init_opt opt = mtmd_helper_init_opt_default();
    struct mtmd_helper_bitmap_wrapper bmp_wrapper = mtmd_helper_bitmap_init_from_file(
        g_mtmd_ctx, image_path, false, opt);

    if (!bmp_wrapper.bitmap) {
        LOGE("Failed to load image: %s", image_path);
        llama_free(ctx);
        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(prompt, prompt_text);
        return env->NewStringUTF("{\"error\":\"image load failed\"}");
    }

    // Qwen2.5-VL chat template; mtmd replaces <__media__> with the image tokens
    std::string full_prompt =
        "<|im_start|>system\nYou are a precise meter reading assistant.<|im_end|>\n"
        "<|im_start|>user\n<__media__>";
    full_prompt += prompt_text;
    full_prompt += "<|im_end|>\n<|im_start|>assistant\n";

    mtmd_input_text full_text;
    full_text.text = full_prompt.c_str();
    full_text.text_len = full_prompt.size();
    full_text.add_special = false;
    full_text.parse_special = true;

    const mtmd_bitmap * bitmaps[] = { bmp_wrapper.bitmap };

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    int32_t tokenize_result = mtmd_tokenize(g_mtmd_ctx, chunks, &full_text, bitmaps, 1);
    if (tokenize_result != 0) {
        LOGE("Failed to tokenize input: %d", tokenize_result);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bmp_wrapper.bitmap);
        llama_free(ctx);
        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(prompt, prompt_text);
        return env->NewStringUTF("{\"error\":\"tokenization failed\"}");
    }

    // Evaluate all chunks (text + image)
    llama_pos new_n_past = 0;
    int32_t eval_result = mtmd_helper_eval_chunks(
        g_mtmd_ctx, ctx, chunks,
        0,       // n_past
        0,       // seq_id
        512,     // n_batch
        true,    // logits_last
        &new_n_past);

    if (eval_result != 0) {
        LOGE("Eval failed with code: %d", eval_result);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bmp_wrapper.bitmap);
        llama_free(ctx);
        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(prompt, prompt_text);
        return env->NewStringUTF("{\"error\":\"eval failed\"}");
    }

    // Generate response tokens
    std::string response;
    const int max_tokens = 24;
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    for (int i = 0; i < max_tokens; i++) {
        llama_token token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(llama_model_get_vocab(g_model), token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            response.append(buf, n);
        }

        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }

    LOGI("Response: %s", response.c_str());

    llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp_wrapper.bitmap);
    llama_free(ctx);

    env->ReleaseStringUTFChars(imagePath, image_path);
    env->ReleaseStringUTFChars(prompt, prompt_text);

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_technavious_om15_ai_LlamaEngine_freeModel(
    JNIEnv *env, jobject /* this */) {

    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    LOGI("Model freed");
}

JNIEXPORT jboolean JNICALL
Java_com_technavious_om15_ai_LlamaEngine_isLoaded(
    JNIEnv *env, jobject /* this */) {
    return (g_model != nullptr && g_mtmd_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
