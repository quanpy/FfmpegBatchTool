Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

jarPath = "target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar"

If fso.FileExists(jarPath) Then
    WshShell.Run "javaw -jar " & jarPath, 0, False
Else
    result = MsgBox("JAR file does not exist. Build project?", vbYesNo + vbQuestion, "FFmpeg Processor")
    
    If result = vbYes Then
        buildCmd = "cmd.exe /c mvn clean package"
        returnCode = WshShell.Run(buildCmd, 1, True)
        
        If fso.FileExists(jarPath) Then
            MsgBox "Build successful! Starting application...", vbInformation, "FFmpeg Processor"
            WshShell.Run "javaw -jar " & jarPath, 0, False
        Else
            MsgBox "Build failed. Make sure Maven is installed.", vbExclamation, "Error"
        End If
    End If
End If 