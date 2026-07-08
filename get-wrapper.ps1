# 第一次使用前运行：下载 gradle-wrapper.jar（若 GitHub 访问不畅，见下方说明）
# 用法：在 VXBrowser 目录下执行  powershell -ExecutionPolicy Bypass -File get-wrapper.ps1
$url = "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
$out = "gradle/wrapper/gradle-wrapper.jar"
New-Item -ItemType Directory -Force -Path "gradle/wrapper" | Out-Null
try {
    Invoke-WebRequest -Uri $url -OutFile $out
    Write-Host "gradle-wrapper.jar 下载完成 -> $out"
} catch {
    Write-Host "下载失败（可能是网络/GFW 限制）。两种替代方案："
    Write-Host "1) 手动下载 Gradle 8.11.1 压缩包：https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
    Write-Host "   解压后把  gradle-8.11.1/lib/plugins/gradle-wrapper-8.11.1.jar  改名为 gradle-wrapper.jar 放到 gradle/wrapper/ 目录"
    Write-Host "2) 或者直接用系统 Gradle：下载上面压缩包，解压并将 bin 加入 PATH，然后执行  gradle assembleDebug"
}
