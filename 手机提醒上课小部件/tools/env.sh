# 项目本地构建环境（不写任何全局变量，不碰系统 PATH）
# 用法：在项目根目录执行  source tools/env.sh
#
# 说明：
# - JAVA_HOME/ANDROID_HOME 用正斜杠 D:/ 形式（Gradle / sdkmanager.bat 识别）
# - git-bash(MSYS2) 的 PATH 按 ":" 分隔，条目里的 "D:" 会被截断，
#   所以 PATH 内必须用 POSIX 形式 /d/...（msys 启动原生进程时会自动转回 Windows 格式）

PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$PROJ_ROOT/tools/jdk-17"
export ANDROID_HOME="$PROJ_ROOT/tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

export PATH="$PROJ_ROOT/tools/jdk-17/bin:$PROJ_ROOT/tools/android-sdk/cmdline-tools/latest/bin:$PROJ_ROOT/tools/android-sdk/platform-tools:$PATH"
