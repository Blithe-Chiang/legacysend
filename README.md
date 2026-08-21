# 旧版互传（LegacySend）

一个独立实现的原生 Android Java 局域网文件传输应用，目标是兼容 LocalSend Protocol v2.1 的设备发现和 Upload API。项目不引用、导入或构建相邻的 LocalSend 源码目录。

当前版本：`1.3.0`（versionCode 5）。

## 构建

要求：

- JDK 17
- Gradle Wrapper 8.9
- Android Gradle Plugin 8.7.3
- Android SDK Platform 34
- Android SDK Build Tools 34.0.0

构建与验证命令：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目 `minSdkVersion` 为 19，`targetSdkVersion` 为 28。目标 SDK 维持在 28，是为了让 API 19–28 使用同一套旧式公共下载目录权限语义；本项目不面向 Google Play 发布。

## 功能

- UDP 组播设备发现、公告和回包
- HTTP/HTTPS LocalSend v2 注册接口
- 单文件和多文件选择、发送和接收
- 可选择并持久保存接收目录，支持系统文档目录和 Android 4.4 文件系统目录
- 接收前接受/拒绝
- 文件级令牌、会话 ID、来源 IP 和证书指纹检查
- 流式上传和保存，不将完整文件载入内存
- 总体进度、失败提示和双方取消
- 同名文件自动添加 `(1)`、`(2)` 后缀
- 中文、空格和常见特殊字符文件名
- 前台接收服务，Activity 重建不终止当前传输
- 所有可见文案均为简体中文

## 主要目录

```text
app/src/main/java/com/blithe/legacysend/
├── LegacySendApp.java       应用级状态、后台任务和 UI 事件
├── ReceiveService.java      前台保活接收服务
├── discovery/               UDP 组播发现
├── model/                   设备和文件模型
├── protocol/                LocalSend JSON 格式
├── security/                自签名身份、mTLS、证书指纹固定、TLS 1.2
├── server/                  HTTP/HTTPS 注册、准备、上传、取消接口
├── storage/                 SAF 文件信息、保存目录和重名处理
├── transfer/                HTTPS 发送客户端和进度/取消
├── ui/                      传统 Android View 中文界面
└── util/                    流式复制和进度工具
```

业务源码全部为 Java；没有 Kotlin、Compose、Flutter、Dart 或 React Native 代码。Gradle 使用 Groovy DSL。

## Android 4.4.2 处理

- 使用 API 19 可用的 `ACTION_OPEN_DOCUMENT` 和 `ClipData` 多选文件。
- API 19–28 默认将接收文件保存到公共 `Download/LegacySend`，使用旧版存储权限。
- API 19–20 通过应用内目录浏览器设置保存位置；API 21+ 通过系统目录选择器持久授权保存位置。
- API 19–20 使用应用内旧版文件浏览器，绕过部分 Kindle 固件中会显示已删除下载记录的 DocumentsUI；可重复选择以添加多个文件。
- API 29+ 默认保存到应用专属 `Android/data/com.blithe.legacysend/files/Download/LegacySend`，也可通过系统目录选择器保存到用户授权目录。
- `AndroidKeyStore` 使用 API 18 引入的 `KeyPairGeneratorSpec` 生成 RSA 自签名设备身份。
- API 19 上显式启用 TLS 1.2，同时保留对 TLS 1.0/1.1 的协商能力。
- API 19–20 接收服务使用 LocalSend v2 官方定义的 HTTP 模式：旧版系统的 TLS 服务端仅支持 CBC 密码套件，与 LocalSend 1.17.0 的现代 Rust TLS 客户端没有共同套件。API 21+ 接收服务保持 HTTPS。
- 获取 `MulticastLock` 后监听组播；网络和文件 I/O 全部在后台线程执行。
- 文件使用 32 KiB 缓冲流式复制，并验证实际字节数与元数据大小一致。
- 前台 Service 为 API 19 使用传统通知，为 API 26+ 创建通知频道。
- Activity 使用应用级控制器承载会话，旋转和界面重建不会直接销毁传输对象。

## 依赖

运行时没有第三方依赖，仅使用 Android SDK、Java 标准库和 `org.json`（Android 系统自带）。因此运行依赖不存在额外的 API 19 兼容风险。

测试依赖：

- JUnit 4.13.2：仅在主机 JVM 运行测试，不打入 APK。
- `org.json:json:20240303`：仅为主机 JVM 测试提供与 Android `org.json` 对应的实现，不打入 APK。

## 当前验证状态

### 已实现并经过测试

- 主机单元测试 13 项：协议序列化、多文件元数据、中文/特殊字符、接受/拒绝/取消/超时、重名、进度、流式复制、中断检测和内容哈希一致性。
- Gradle 编译、Lint 和 debug APK 打包。
- APK 清单检查确认 `minSdkVersion=19`、`targetSdkVersion=28`。
- APK v1/v2 签名校验；v1 签名可供 Android 4.4.2 安装。
- API 34 ARM64 模拟器冷启动、设备证书生成、HTTPS 53317 服务、前台 Service。
- API 34 模拟器上的 `/info` 和 `/register` HTTPS 实际请求。
- API 34 模拟器上的接收确认、中文文件名上传、18 字节流式落盘和内容读取一致性。
- Kindle Android 4.4.2（API 19）真机安装、启动、应用内文件选择，以及向 Android 11 设备发送 5.2 KB 文件成功。
- Kindle Android 4.4.2（API 19）真机选择接收目录、重启后设置保留，以及恢复默认目录。
- 官方 LocalSend 1.17.0（Android 11）发现 Kindle，并向 Kindle Android 4.4.2 发送 6.3 KB 文件成功；发送端和接收端均显示完成，接收文件 SHA-256 与源文件一致。

## Kindle 4.4.2 文件选择修复

Kindle 固件自带的 DocumentsUI 会保留已经被删除或移动的下载记录。旧版应用能够查询这些记录的名称和大小，但发送时重新打开对应 `content://` URI 会收到 `FileNotFoundException: No such file or directory`。版本 1.1 在 API 19–20 改用应用内文件浏览器，直接列出实际存在且可读的外部存储文件；API 21+ 继续使用系统 SAF。对于其他无效文件来源，发送错误也会显示明确的中文提示。

### 已实现但尚未真机验证

- 与官方 LocalSend 的多文件传输、拒绝和取消。
- Android 真机上的 Wi-Fi 网络变化、厂商后台限制和大文件长时间传输。

### 尚未实现

- LocalSend Reverse Download API（浏览器下载）；核心 Android-to-LocalSend Upload API 不依赖它。
- PIN、历史记录、文字分享、剪贴板、主题、自动更新、统计和账户等非核心功能。
- 子网逐地址扫描回退；当前使用官方默认组播发现和 `/register` 双向确认。

### 因环境限制无法验证

- Apple Silicon 主机的 Android Emulator 36 不支持 API 19 的 ARMv7 QEMU2 镜像，启动时明确报错 `CPU Architecture 'arm' is not supported by the QEMU2 emulator`。

## API 19–20 接收模式的安全边界

Android 4.4 的系统 TLS 服务端与 LocalSend 1.17.0 没有共同密码套件，因此 API 19–20 只能以协议规定的 HTTP 模式接收。该方向的文件内容和元数据不会被 TLS 加密；应用仍检查来源 IP、随机会话 ID 和逐文件随机 token。请只在可信局域网使用。LegacySend 向其他设备发送仍使用 HTTPS 和证书指纹固定，API 21+ 的接收服务也仍使用 HTTPS。

协议研究和端点细节见 [docs/protocol.md](docs/protocol.md)。

## 贡献

欢迎提交 Issue 和 Pull Request。LegacySend 的首要目标是在 Kindle Android 4.4.2（API 19）上提供精简、稳定且可与官方 LocalSend 互通的文件传输能力；修改时请优先保护旧系统兼容性，而不是扩大功能范围。

### 实现边界

- 保持独立实现：不要导入、复制或构建 LocalSend 的源码、资源、模块或内部库。可以依据公开协议文档、公开源码中的协议行为以及黑盒测试进行兼容实现。
- 业务代码使用 Java 和传统 Android View，不引入 Kotlin、Compose、Flutter、React Native 或 Google Play 服务。
- 保持 `minSdkVersion 19`。调用更高版本 API 前必须做版本判断，并为 API 19 提供可用路径。
- 运行时优先使用 Android SDK 和 Java 标准库。新增依赖必须说明用途、体积以及 API 19 兼容性。
- 核心范围是设备发现、文件发送/接收、确认/拒绝/取消、进度和错误处理；历史记录、账户、云中转、自动更新等不属于当前目标。

### 开发要求

- 协议端口、请求路径、JSON 字段、状态码、证书指纹和版本协商必须有公开协议或实际通信行为作为依据，避免凭印象实现。
- 网络与文件 I/O 必须在后台线程运行；文件应流式处理，不能完整载入内存，并正确关闭 Socket 和流。
- 修改存储、通知、TLS、文件选择或生命周期逻辑时，应分别检查 API 19 和现代 Android 的行为。
- 保持模块职责清晰：发现、协议、安全、服务端、存储、传输和 UI 逻辑不要集中到单个 Activity。
- 测试截图、Gradle 缓存、构建目录和本机配置不要提交；Gradle Wrapper 的脚本、JAR 和配置应保留，以支持干净环境构建。

### 提交前检查

运行完整的本地检查：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

与协议或传输有关的改动应补充测试，至少覆盖相关的序列化、中文/特殊字符文件名、多文件会话、接受/拒绝/取消、超时、流式复制和内容一致性场景。提交说明或 PR 中请明确区分：

- 已通过自动化测试的内容；
- 已在 Android 真机或模拟器验证的内容；
- 已与官方 LocalSend 实际互传验证的内容；
- 尚未验证的风险或环境限制。

不要把编译成功等同于 Android 4.4.2 真机兼容，也不要把单元测试通过描述为已经完成官方 LocalSend 互传验证。
