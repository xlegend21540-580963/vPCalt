#include "vortex_native.h"
#include <aaudio/AAudio.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#define RING_CAPACITY_SAMPLES (48000 * 2) // 1 second stereo 48kHz ring buffer

static AAudioStream* g_stream = NULL;
static int16_t* g_ring = NULL;
static atomic_uint g_read_idx = 0;
static atomic_uint g_write_idx = 0;
static int g_channels = 2;

static aaudio_data_callback_result_t aaudio_callback(
    AAudioStream* stream,
    void* user_data,
    void* audio_data,
    int32_t num_frames
) {
    (void)stream;
    (void)user_data;
    int16_t* out = (int16_t*)audio_data;
    int32_t samples_needed = num_frames * g_channels;

    unsigned int r = atomic_load_explicit(&g_read_idx, memory_order_relaxed);
    unsigned int w = atomic_load_explicit(&g_write_idx, memory_order_acquire);

    for (int32_t i = 0; i < samples_needed; ++i) {
        if (r != w && g_ring != NULL) {
            out[i] = g_ring[r % RING_CAPACITY_SAMPLES];
            r++;
        } else {
            out[i] = 0;
        }
    }
    atomic_store_explicit(&g_read_idx, r, memory_order_release);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

int vortex_audiobridge_start(int sample_rate, int channel_count, int frames_per_burst) {
    vortex_audiobridge_stop();

    g_channels = channel_count > 0 ? channel_count : 2;
    g_ring = (int16_t*)calloc(RING_CAPACITY_SAMPLES, sizeof(int16_t));
    if (!g_ring) return -12;
    atomic_store(&g_read_idx, 0);
    atomic_store(&g_write_idx, 0);

    AAudioStreamBuilder* builder = NULL;
    aaudio_result_t res = AAudio_createStreamBuilder(&builder);
    if (res != AAUDIO_OK || !builder) return res;

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, sample_rate > 0 ? sample_rate : 48000);
    AAudioStreamBuilder_setChannelCount(builder, g_channels);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    if (frames_per_burst > 0) {
        AAudioStreamBuilder_setFramesPerDataCallback(builder, frames_per_burst);
    }
    AAudioStreamBuilder_setDataCallback(builder, aaudio_callback, NULL);

    res = AAudioStreamBuilder_openStream(builder, &g_stream);
    AAudioStreamBuilder_delete(builder);
    if (res != AAUDIO_OK || !g_stream) {
        vortex_audiobridge_stop();
        return res;
    }

    res = AAudioStream_requestStart(g_stream);
    if (res != AAUDIO_OK) {
        vortex_audiobridge_stop();
        return res;
    }
    return 0;
}

int vortex_audiobridge_write(const int16_t* pcm_data, int frame_count) {
    if (!g_ring || !pcm_data || frame_count <= 0) return 0;
    int samples = frame_count * g_channels;
    unsigned int w = atomic_load_explicit(&g_write_idx, memory_order_relaxed);
    unsigned int r = atomic_load_explicit(&g_read_idx, memory_order_acquire);

    int written = 0;
    for (int i = 0; i < samples; ++i) {
        if ((w - r) >= RING_CAPACITY_SAMPLES) break;
        g_ring[w % RING_CAPACITY_SAMPLES] = pcm_data[i];
        w++;
        written++;
    }
    atomic_store_explicit(&g_write_idx, w, memory_order_release);
    return written / g_channels;
}

void vortex_audiobridge_stop(void) {
    if (g_stream) {
        AAudioStream_requestStop(g_stream);
        AAudioStream_close(g_stream);
        g_stream = NULL;
    }
    if (g_ring) {
        free(g_ring);
        g_ring = NULL;
    }
}
