> [!WARNING]
> ### ⚠️ Critical: Server Shutdown & Linear / b_linear Region Formats
>
> If you are using any Linear region format (**Linear v1, v2, v3** or **b_linear**), **NEVER force-kill** (`kill -9`, `SIGKILL`, **panel crash/stop buttons**) the server process!
>
> Always shut down or restart cleanly using `/stop` or `/restart`.
>
> **Known risks and limitations with Linear formats:**
> - **Catastrophic Chunk / Region Corruption:** Linear formats buffer and compress chunks into shared Zstandard/LZ4 data blocks rather than separate Anvil `.mca` sector slots. Abruptly terminating the process while a flush or write-ahead-log (WAL) sync is in progress can corrupt entire region files (up to 1,024 chunks at once).
> - **Data Loss on Hard Crashes:** If the host machine loses power, suffers an OOM-killer termination, or is abruptly stopped without executing shutdown hooks, unwritten in-memory chunk buffers cannot be recovered.
> - **Incompatibility & Tooling Issues:** Linear v3 and non-standard specifications are not supported by external tools (such as map renderers like Dynmap/BlueMap, world converters, or NBT editors). Always keep reliable backups before migrating.

<div align="center">
<img src="image/mint.png" alt="Mint" width="500">

### Mint is based on Folia, offering better high-performance concurrency and restoring vanilla mechanics

![GitHub Repo stars](https://img.shields.io/github/stars/MenthaMC/Mint?style=for-the-badge&logo=github&label=Stars&logoColor=white&color=ffda65)
![GitHub Build](https://img.shields.io/github/actions/workflow/status/MenthaMC/Mint/build.yml?style=for-the-badge&logo=github&label=Build&logoColor=white&color=06d094)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/MenthaMC/Mint/total?style=for-the-badge&logo=github&label=Downloads&logoColor=white&color=c4a400)
</div>

## ✨ Features
- Configurable vanilla features
- Monitoring for Tpsbar, Membar, Regionbar, Networkbar
- Integrated optimizations from various forks
- Optimizations for single-threaded region performance
- Configurable region file formats (`MCA`, `LINEAR_V2` using header format v3 with existence bitmap fix, `B_LINEAR`)
- Bug fixes
- Improved stability
- Integrated [Sentry](https://sentry.io/welcome/) from [Pufferfish](https://github.com/pufferfish-gg/Pufferfish) for easy, detailed tracking of all server errors
- NetworkAnalyser network packet analysis
- And more!

## 📦 Download or Build
Any version can be found in [Releases](https://github.com/MenthaMC/Mint/releases), or can be built via:
```shell
./gradlew applyAllPatches && ./gradlew createPaperclipJar
```

## 📫 Contact
**QQ Group: [1020403749](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=_UmBe7SYb9kBrh8pvTGr1aGPygk5DfF7&authKey=cCQ1U%2FTBIG8su93cGQK4rfm5vtqwXF3BSUz%2FAvd8st2S9BQ3PFeVHzbNAdFuSNWK&noverify=0&group_code=1020403749)** | **Discord: [Click to join](https://discord.gg/PK4YAtAHpr)**

## 📈 bStats
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Mint.svg)](https://bstats.org/plugin/server-implementation/Mint)

## 🧪 API
### Gradle
```groovy
maven {
    name = "menthamc"
    url = "https://repo.menthamc.org/repository/maven-public/"
}
dependencies {
    compileOnly("dev.bacteriawa.mint:mint-api:26.1.2.build.+")
}
```
### Maven
```xml
<repository>
    <id>menthamc</id>
    <url>https://repo.menthamc.org/repository/maven-public/</url>
</repository>
```
```xml
<dependency>
    <groupId>dev.bacteriawa.mint</groupId>
    <artifactId>mint-api</artifactId>
    <version>[26.1.2.build,)</version>
    <scope>provided</scope>
</dependency>
```

## Please give us a ⭐ Star!
> [!TIP]
> Every free ⭐ Star is our motivation to keep moving forward!
> 
[![Star History Chart](https://api.star-history.com/svg?repos=MenthaMC/Mint&type=Date)](https://star-history.com/#MenthaMC/Mint&Date)

---

*Originally written in Chinese (中文), translated into English by AI.*
