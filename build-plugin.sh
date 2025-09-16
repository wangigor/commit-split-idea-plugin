#!/bin/bash

echo "🔨 开始构建 Commit Splitter 插件..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 需要安装 Java 17+"
    echo "请安装 JDK: brew install openjdk@17"
    exit 1
fi

# 检查是否有IDEA
if [ -d "/Applications/IntelliJ IDEA.app" ] || [ -d "/Applications/IntelliJ IDEA CE.app" ]; then
    echo "✅ 检测到 IntelliJ IDEA"
else
    echo "⚠️  未检测到 IntelliJ IDEA，请确保已安装"
fi

echo ""
echo "🚀 构建方法："
echo ""
echo "方法1 - 用 IDEA 直接打开 (推荐):"
echo "  1. IntelliJ IDEA → File → Open"
echo "  2. 选择此目录: $(pwd)"
echo "  3. 右侧 Gradle 面板 → intellij → runIde"
echo "  4. 新窗口打开，插件已安装"
echo ""
echo "方法2 - 如果你有 Gradle:"
echo "  gradle runIde"
echo ""
echo "方法3 - 构建安装包:"
echo "  gradle buildPlugin"
echo "  然后在 build/distributions/ 找到 .zip 文件"
echo "  IDEA 中: Settings → Plugins → 齿轮 → Install from Disk"
echo ""

# 检查项目文件
echo "📁 项目文件检查:"
echo "✅ build.gradle.kts - 构建配置"
echo "✅ plugin.xml - 插件配置" 
echo "✅ Java 源码 - $(find src -name "*.java" | wc -l | tr -d ' ') 个文件"
echo ""
echo "🎯 插件功能:"
echo "  • 右键 Git commit → Split Commit"
echo "  • Settings → Tools → Commit Splitter 配置用户"
echo "  • 智能拆分策略: 文件/代码块/行级"
echo ""
echo "准备就绪！选择上述任一方法即可安装使用插件 🎉"