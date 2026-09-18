/*
 * Copyright (C) 2026 CommVault AI Audio Bridge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "virtual_audio_hw"
#include <log/log.h>
#include <hardware/audio.h>
#include <hardware/hardware.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#define PCM_PIPE_PATH "/data/local/tmp/virtual_mic.pcm"

struct virtual_audio_device {
    struct audio_hw_device device;
};

struct virtual_stream_in {
    struct audio_stream_in stream;
    int pipe_fd;
};

static uint32_t in_get_sample_rate(const struct audio_stream *stream) { return 48000; }
static int in_set_sample_rate(struct audio_stream *stream, uint32_t rate) { return 0; }
static size_t in_get_buffer_size(const struct audio_stream *stream) { return 1024 * 4; }
static audio_channel_mask_t in_get_channels(const struct audio_stream *stream) { return AUDIO_CHANNEL_IN_STEREO; }
static audio_format_t in_get_format(const struct audio_stream *stream) { return AUDIO_FORMAT_PCM_16_BIT; }
static int in_set_format(struct audio_stream *stream, audio_format_t format) { return 0; }
static int in_standby(struct audio_stream *stream) { return 0; }
static int in_dump(const struct audio_stream *stream, int fd) { return 0; }
static int in_set_parameters(struct audio_stream *stream, const char *kvpairs) { return 0; }
static char * in_get_parameters(const struct audio_stream *stream, const char *keys) { return strdup(""); }
static int in_add_audio_effect(struct audio_stream *stream, effect_handle_t effect) { return 0; }
static int in_remove_audio_effect(struct audio_stream *stream, effect_handle_t effect) { return 0; }
static int in_set_gain(struct audio_stream_in *stream, float gain) { return 0; }

static ssize_t in_read(struct audio_stream_in *stream, void* buffer, size_t bytes) {
    struct virtual_stream_in *in = (struct virtual_stream_in *)stream;
    if (in->pipe_fd < 0) {
        in->pipe_fd = open(PCM_PIPE_PATH, O_RDONLY);
        if (in->pipe_fd < 0) {
            // Pipe doesn't exist yet, just send silence
            memset(buffer, 0, bytes);
            usleep(bytes * 1000000 / (48000 * 4)); // 4 bytes per frame (stereo 16bit)
            return bytes;
        }
    }

    ssize_t read_bytes = read(in->pipe_fd, buffer, bytes);
    if (read_bytes <= 0) {
        // EOF or error, pipe closed or not written to
        memset(buffer, 0, bytes);
        usleep(bytes * 1000000 / (48000 * 4));
        close(in->pipe_fd);
        in->pipe_fd = -1;
        return bytes;
    }

    return read_bytes;
}

static uint32_t in_get_input_frames_lost(struct audio_stream_in *stream) { return 0; }

static int adev_open_input_stream(struct audio_hw_device *dev,
                                  audio_io_handle_t handle,
                                  audio_devices_t devices,
                                  struct audio_config *config,
                                  struct audio_stream_in **stream_in,
                                  audio_input_flags_t flags,
                                  const char *address,
                                  audio_source_t source) {
    struct virtual_stream_in *in = calloc(1, sizeof(struct virtual_stream_in));
    if (!in) return -ENOMEM;

    in->stream.common.get_sample_rate = in_get_sample_rate;
    in->stream.common.set_sample_rate = in_set_sample_rate;
    in->stream.common.get_buffer_size = in_get_buffer_size;
    in->stream.common.get_channels = in_get_channels;
    in->stream.common.get_format = in_get_format;
    in->stream.common.set_format = in_set_format;
    in->stream.common.standby = in_standby;
    in->stream.common.dump = in_dump;
    in->stream.common.set_parameters = in_set_parameters;
    in->stream.common.get_parameters = in_get_parameters;
    in->stream.common.add_audio_effect = in_add_audio_effect;
    in->stream.common.remove_audio_effect = in_remove_audio_effect;
    in->stream.set_gain = in_set_gain;
    in->stream.read = in_read;
    in->stream.get_input_frames_lost = in_get_input_frames_lost;
    
    in->pipe_fd = -1;

    *stream_in = &in->stream;
    return 0;
}

static void adev_close_input_stream(struct audio_hw_device *dev, struct audio_stream_in *stream) {
    struct virtual_stream_in *in = (struct virtual_stream_in *)stream;
    if (in->pipe_fd >= 0) {
        close(in->pipe_fd);
    }
    free(stream);
}

static int adev_set_parameters(struct audio_hw_device *dev, const char *kvpairs) { return 0; }
static char * adev_get_parameters(const struct audio_hw_device *dev, const char *keys) { return strdup(""); }
static int adev_init_check(const struct audio_hw_device *dev) { return 0; }
static int adev_set_voice_volume(struct audio_hw_device *dev, float volume) { return 0; }
static int adev_set_master_volume(struct audio_hw_device *dev, float volume) { return 0; }
static int adev_get_master_volume(struct audio_hw_device *dev, float *volume) { return 0; }
static int adev_set_mode(struct audio_hw_device *dev, audio_mode_t mode) { return 0; }
static int adev_set_mic_mute(struct audio_hw_device *dev, bool state) { return 0; }
static int adev_get_mic_mute(const struct audio_hw_device *dev, bool *state) { return 0; }
static size_t adev_get_input_buffer_size(const struct audio_hw_device *dev, const struct audio_config *config) { return 1024 * 4; }

static int adev_close(hw_device_t *device) {
    free(device);
    return 0;
}

static int adev_open(const hw_module_t* module, const char* name, hw_device_t** device) {
    struct virtual_audio_device *adev = calloc(1, sizeof(struct virtual_audio_device));
    if (!adev) return -ENOMEM;

    adev->device.common.tag = HARDWARE_DEVICE_TAG;
    adev->device.common.version = AUDIO_DEVICE_API_VERSION_2_0;
    adev->device.common.module = (struct hw_module_t *) module;
    adev->device.common.close = adev_close;

    adev->device.init_check = adev_init_check;
    adev->device.set_voice_volume = adev_set_voice_volume;
    adev->device.set_master_volume = adev_set_master_volume;
    adev->device.get_master_volume = adev_get_master_volume;
    adev->device.set_mode = adev_set_mode;
    adev->device.set_mic_mute = adev_set_mic_mute;
    adev->device.get_mic_mute = adev_get_mic_mute;
    adev->device.set_parameters = adev_set_parameters;
    adev->device.get_parameters = adev_get_parameters;
    adev->device.get_input_buffer_size = adev_get_input_buffer_size;
    adev->device.open_input_stream = adev_open_input_stream;
    adev->device.close_input_stream = adev_close_input_stream;

    *device = &adev->device.common;
    return 0;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = adev_open,
};

struct audio_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = AUDIO_MODULE_API_VERSION_0_1,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = AUDIO_HARDWARE_MODULE_ID,
        .name = "Virtual Audio HAL",
        .author = "CommVault AI",
        .methods = &hal_module_methods,
    },
};
