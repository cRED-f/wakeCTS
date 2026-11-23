# wakeCTS

An utility that puts **Circle to Search** within reach through a draggable floating button. Includes an **LSPosed module** to route supported Assistant requests to Circle to Search.

## Features

- **Floating shortcut:** Tap to trigger Circle to Search

## Requirements

- Android **8.0+** (API 26+).
- **LSPosed** with access to **System Framework / Android System** for the Circle to Search hook.
- A device with a compatible Circle to Search implementation; availability varies by device and ROM.

## Installation

1. Launch **wakeCTS** and enable its **Accessibility service**.
2. In **LSPosed**, enable **wakeCTS**, select **System Framework / Android System** in its scope, and reboot.
3. Return to wakeCTS and tap **Test Circle to Search**. Optionally add the **wakeCTS** tile from Android's Quick Settings editor.

> If Circle to Search cannot be triggered, wakeCTS can open the system Assistant when the fallback setting is enabled. The floating shortcut can be disabled without turning off the Accessibility service.

