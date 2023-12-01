$log = "cntlm-restart_$(Get-Date -format "yyyy-MM-dd").log"  
$logpath = "C:\Logs\cntlm_restart_job\$log" 
$ServiceName = 'cntlm' 
$ProcessName = 'cntlm.exe' 
 
try { # Check Internt connection 
    Invoke-WebRequest -Uri ya.ru -Method Get -UseBasicParsing
    #Write-Output "$(Get-Date) No action required" >> $logpath
} 
catch { # Trying to fix the service
$status = (Get-Service | Where-Object { $_.name -eq $ServiceName }).status 
    if ($status -eq "Running") { 
        try { 
            # Trying to stop the service 
            Stop-Service -Name $ServiceName 
            Write-Output "$(Get-Date) Service stoppped" >> $logpath 
        } 
        catch { 
            # If stop service failed - kill the process 
            Stop-Process -name $ProcessName -Force 
            Write-Output "$(Get-Date) Process killed" >> $logpath 
        } 
        # Wait for N seconds and rechecking service state
        Start-Sleep -Seconds 5 
        $status = (Get-Service | Where-Object { $_.name -eq $ServiceName }).status 
    } 
    else { 
        Write-Output "$(Get-Date) Service down" >> $logpath 
    } 
     
    if ($status -eq "Stopped") { 
        try { 
            Start-Service -Name $ServiceName 
            Write-Output "$(Get-Date) Service started" >> $logpath 
        } 
        catch { 
            Write-Output "$(Get-Date) Unable to start the service" >> $logpath 
        }   
    } 
}
