$jdk21 = "C:\Program Files\Java\jdk-21"

if (-not (Test-Path $jdk21)) {
    Write-Host "未找到 JDK 21：$jdk21" -ForegroundColor Red
    Write-Host "请先安装 JDK 17 或更高版本。"
    exit 1
}

$env:JAVA_HOME = $jdk21
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$tmpDir = "E:\nsu-edu-maic\tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$env:MAVEN_OPTS = "-Djava.io.tmpdir=$tmpDir"

Write-Host "使用 JAVA_HOME=$env:JAVA_HOME"
Write-Host "启动后端：http://localhost:8080"

mvn spring-boot:run
