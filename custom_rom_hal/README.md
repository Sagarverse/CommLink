# Native Virtual Audio Device (Custom ROM Integration)

This directory contains the C/C++ source code required to compile a Native Virtual Audio Device Hardware Abstraction Layer (HAL) for Android.

When compiled and integrated into a Custom ROM (AOSP/LineageOS), this HAL creates a system-level virtual microphone that reads raw PCM audio data directly from a pipe (`/data/local/tmp/virtual_mic.pcm`) that the CommLink app writes to.

## Integration Steps for AOSP

1. **Copy the Source Code**
   Copy this `custom_rom_hal` directory into your Android Source Tree under `hardware/interfaces/audio/` or directly inside your device tree (e.g. `device/<vendor>/<device>/audio/`).

2. **Include the Module in the Build**
   In your device's `device.mk`, add the new HAL library to `PRODUCT_PACKAGES`:
   ```makefile
   PRODUCT_PACKAGES += \
       audio.primary.virtual
   ```

3. **Update Audio Policy Configuration**
   You must modify your device's `audio_policy_configuration.xml` to define the new virtual input device. Add an attached device to your primary module:
   ```xml
   <devicePorts>
       <devicePort tagName="Virtual In" type="AUDIO_DEVICE_IN_STUB" role="source" address="virtual"/>
   </devicePorts>
   <routes>
       <route type="mix" sink="primary input" sources="Virtual In,..."/>
   </routes>
   ```

4. **SELinux Policies**
   You will need to ensure `audioserver` has the SEPolicy permission to read from `/data/local/tmp/virtual_mic.pcm`.
   Add this to your `audioserver.te`:
   ```sepolicy
   allow audioserver shell_data_file:file { read open getattr };
   ```

5. **Build and Flash**
   ```bash
   source build/envsetup.sh
   lunch <your_device>-userdebug
   make -j$(nproc)
   ```
   Flash the resulting `system.img` and `vendor.img` to your device.

6. **Use with CommLink App**
   Open the CommLink app, select **Native Virtual Device** as the Virtual Microphone Method, and hit **Start Both**. The app will automatically pipe the USB audio stream to `/data/local/tmp/virtual_mic.pcm` where this custom HAL will pick it up and feed it into the Android audio system without acoustic echo!
