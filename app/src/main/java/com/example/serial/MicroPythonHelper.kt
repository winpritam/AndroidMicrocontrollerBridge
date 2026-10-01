package com.example.serial

object MicroPythonHelper {

    const val CTRL_A = "\u0001" // Enter raw REPL
    const val CTRL_B = "\u0002" // Exit raw REPL
    const val CTRL_C = "\u0003" // Interrupt / Break
    const val CTRL_D = "\u0004" // Soft reset

    fun generateDeviceCode(
        sdaPin: Int = 21,
        sclPin: Int = 22,
        i2cBus: Int = 0,
        i2cFreq: Int = 400000,
        width: Int = 128,
        height: Int = 64,
        frameDelayMs: Int = 120,
        defaultAnimation: String = "anims/emo_happy.bin"
    ): String {
        return """
# Picocase - MicroPython OLED Companion Driver
# Compatible with ESP32, Raspberry Pi Pico, and MicroPython microcontrollers
# SSD1306 ${width}x${height}
# SDA = GPIO$sdaPin, SCL = GPIO$sclPin

from machine import Pin, I2C
import ssd1306
import framebuf
import time
import os
import sys
import select

WIDTH = $width
HEIGHT = $height
FRAME_SIZE = (WIDTH * HEIGHT) // 8  # ${(width * height) / 8} bytes
FRAME_DELAY = $frameDelayMs

# Ensure anims directory exists
try:
    if "anims" not in os.listdir():
        os.mkdir("anims")
except Exception as e:
    pass

# Initialize I2C and OLED
print("Initializing OLED Display...")
try:
    i2c = I2C($i2cBus, scl=Pin($sclPin), sda=Pin($sdaPin), freq=$i2cFreq)
    devices = i2c.scan()
    print("I2C Devices detected:", devices)
    oled = ssd1306.SSD1306_I2C(WIDTH, HEIGHT, i2c)
    oled.fill(0)
    oled.text("PICOCASE", 32, 20)
    oled.text("MicroPython Link", 0, 36)
    oled.text("Ready (115200)", 14, 50)
    oled.show()
except Exception as err:
    print("OLED Init Warning:", err)
    oled = None

# Streaming Frame Buffer (Small RAM Footprint)
buffer = bytearray(FRAME_SIZE)
fb = framebuf.FrameBuffer(buffer, WIDTH, HEIGHT, framebuf.MONO_HLSB)

current_anim_file = "$defaultAnimation"
anim_handle = None
anim_frame_count = 0
anim_current_frame = 0
is_playing = True

upload_handle = None
upload_expected = 0
upload_received = 0

def open_animation(path):
    global anim_handle, anim_frame_count, anim_current_frame, current_anim_file
    if anim_handle:
        try:
            anim_handle.close()
        except:
            pass
        anim_handle = None

    if not path.startswith("anims/") and not path.endswith(".bin"):
        path = "anims/" + path + ".bin"
    elif not path.startswith("anims/"):
        path = "anims/" + path

    try:
        fsize = os.stat(path)[6]
        anim_frame_count = fsize // FRAME_SIZE
        anim_current_frame = 0
        anim_handle = open(path, "rb")
        current_anim_file = path
        print("Loaded animation:", path, "Frames:", anim_frame_count)
        return True
    except Exception as e:
        print("Cannot open animation:", path, e)
        if oled:
            oled.fill(0)
            oled.text("FILE ERROR", 20, 20)
            oled.text(path.split("/")[-1][:16], 0, 38)
            oled.show()
        return False

# Attempt initial animation load
open_animation(current_anim_file)

def list_files_response():
    try:
        lines = []
        if "anims" in os.listdir():
            for f in os.listdir("anims"):
                p = "anims/" + f
                sz = os.stat(p)[6]
                lines.append(f"{f} {sz}")
        for l in lines:
            print(l)
    except Exception as e:
        print("ERROR:", e)
    print("END")

# Main Event & Command Loop
poll_obj = select.poll()
poll_obj.register(sys.stdin, select.POLLIN)

print("READY")

last_frame_time = time.ticks_ms()

while True:
    # 1. Process Incoming Serial Commands Non-blocking
    if poll_obj.poll(0):
        line = sys.stdin.readline()
        if line:
            cmd = line.strip()
            if not cmd:
                pass
            elif cmd == "PING":
                print("READY")
            elif cmd == "START":
                is_playing = True
                print("READY")
            elif cmd == "STOP":
                is_playing = False
                if oled:
                    oled.fill(0)
                    oled.show()
                print("END")
            elif cmd == "LIST_FILES":
                list_files_response()
            elif cmd.startswith("FILE_UPLOAD_BEGIN "):
                parts = cmd.split(" ")
                target_file = parts[1]
                upload_expected = int(parts[2]) if len(parts) > 2 else 0
                upload_received = 0
                if target_file.endswith(".bin") and not target_file.startswith("anims/"):
                    target_file = "anims/" + target_file
                try:
                    upload_handle = open(target_file, "wb")
                    print("READY")
                except Exception as e:
                    print("ERROR:", e)
            elif cmd.startswith("DATA:"):
                hex_data = cmd[5:]
                if upload_handle:
                    try:
                        raw = bytes.fromhex(hex_data)
                        upload_handle.write(raw)
                        upload_received += len(raw)
                        print("READY")
                    except Exception as e:
                        print("ERROR:", e)
                else:
                    print("ERROR: No upload active")
            elif cmd == "FILE_UPLOAD_END":
                if upload_handle:
                    try:
                        upload_handle.close()
                    except:
                        pass
                    upload_handle = None
                print("READY")
            elif cmd.startswith("DELETE "):
                target = cmd[7:].strip()
                if target.endswith(".bin") and not target.startswith("anims/"):
                    target = "anims/" + target
                try:
                    os.remove(target)
                    print("READY")
                except Exception as e:
                    print("ERROR:", e)
            elif cmd.startswith("RENAME "):
                parts = cmd[7:].strip().split(" ")
                if len(parts) >= 2:
                    p1, p2 = parts[0], parts[1]
                    if p1.endswith(".bin") and not p1.startswith("anims/"): p1 = "anims/" + p1
                    if p2.endswith(".bin") and not p2.startswith("anims/"): p2 = "anims/" + p2
                    try:
                        os.rename(p1, p2)
                        print("READY")
                    except Exception as e:
                        print("ERROR:", e)
                else:
                    print("ERROR")
            elif cmd == "EMO_HAPPY":
                open_animation("anims/emo_happy.bin")
                is_playing = True
                print("READY")
            elif cmd == "EMO_SLEEPY":
                open_animation("anims/emo_sleepy.bin")
                is_playing = True
                print("READY")
            elif cmd == "EMO_EXCITED":
                open_animation("anims/emo_excited.bin")
                is_playing = True
                print("READY")
            elif cmd == "ALERT_MSG":
                open_animation("anims/alert_msg.bin")
                is_playing = True
                print("READY")
            elif cmd == "ALERT_CALL":
                open_animation("anims/alert_call.bin")
                is_playing = True
                print("READY")
            elif cmd == "ALERT_CHARGE":
                open_animation("anims/alert_charge.bin")
                is_playing = True
                print("READY")
            elif cmd.startswith("PLAY "):
                target = cmd[5:].strip()
                open_animation(target)
                is_playing = True
                print("READY")
            else:
                # Custom configuration or passthrough
                print("READY")

    # 2. Render Animation Frame
    now = time.ticks_ms()
    if is_playing and oled and anim_handle and (time.ticks_diff(now, last_frame_time) >= FRAME_DELAY):
        last_frame_time = now
        bytes_read = anim_handle.readinto(buffer)
        if bytes_read != FRAME_SIZE:
            anim_handle.seek(0)
            anim_current_frame = 0
            bytes_read = anim_handle.readinto(buffer)

        if bytes_read == FRAME_SIZE:
            oled.fill(0)
            oled.blit(fb, 0, 0)
            oled.show()
            anim_current_frame += 1

    time.sleep_ms(5)
""".trimIndent()
    }
}
