# Nova Relay

**Nova Relay** is a Man-in-the-Middle (MITM) proxy implementation for **Minecraft Bedrock Edition** that enables **packet interception and modification** — **without modifying the Minecraft client**.

## Features

- Seamless switching and adapting between multiple Minecraft versions
- Full control over the packet layer for advanced packet manipulation
- Designed to be lightweight and portable
- Built with Kotlin and modern networking libraries
- Support for RakNet protocol and Bedrock packet handling
- Extensible packet listener system for custom functionality

---

## How to Compile

Follow these steps to compile Nova Relay:

### Prerequisites

- **Java 17** or higher
- **Gradle** (included via wrapper)

### Build Steps

1. **Clone the repository**:

   ```bash
   git clone https://github.com/RadiantByte/Nova-Relay.git
   cd Nova-Relay
   ```

2. **Build the project**:

   ```bash
   ./gradlew build
   ```

3. **Create the standalone JAR**:

   ```bash
   ./gradlew uberJar
   ```

4. After a successful build, the output `.jar` file will be located at:
   ```
   NovaRelay/build/libs/NovaRelay.jar
   ```

---

## How to Use

### Basic Usage

The compiled `NovaRelay.jar` can be used as a standalone proxy to intercept and modify Minecraft Bedrock Edition packets.



### Android Implementation

**Nova Relay's Android client implementation** is located in a separate project: [**Nova Client**](https://github.com/RadiantByte/Nova-Client).

### Supported Minecraft Versions

Nova Relay supports multiple Minecraft Bedrock versions through the Protocol library. The default codec is set to Bedrock v827, but it can be configured to support other versions as needed.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

### Summary of the GPLv3 license:
(Disclaimer: This is **not legal advice**.)

You are allowed to:
- **Use** the code
- **Share** the code
- **Modify** the code

If you use **any** code from this repository:
- You **must disclose** the source code of your modified project
- You **must also disclose** any source code you took from this project
- Your modified application **must also be licensed under GPLv3**
- You **cannot** use this code (even partially) in a **closed-source** or **obfuscated** application

For full information about the GPLv3 license, please visit:  
https://www.gnu.org/licenses/gpl-3.0.en.html

---

## Contributions

We welcome contributions of all kinds!  
Feel free to open a pull request, suggest new features, report bugs, or help improve the project.

---

## Disclaimer

**Please use Nova Relay at your own risk.**  
We **DO NOT** take responsibility for any bans or punishments that may occur as a result of using this tool.