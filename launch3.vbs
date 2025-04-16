Set shell = CreateObject("WScript.Shell")
jar = "target\ffmpeg-batch-wwdx-1.0-SNAPSHOT.jar"
shell.Run "javaw -jar " & jar, 0, False 