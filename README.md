# backupper

backupper is a simple utility for taking backups of mongodb databases and redis server data.

```

usage: backupper
 -b,--bucket-name <arg>                Storage bucket to use
 -c,--cloudUploadPeriodInHours         Cloud Upload Period in hours. The
                                       time period between cloud backups.
                                       Default Value = 24
 -d,--databases <db1,db2>              Database name to backup
 -h,--help                             Prints this help message
 -l,--verbose                          Turn on verbose logging
 -n,--numberOfBackupsOnMachine <arg>   Number of backups to be maintained
                                       on the machine.
                                       Older backups will be deleted while retaining only this number of backups.
                                       Default values = 20
 -r,--redis                            Enable Redis rdb backup
 -v,--version                          Prints the version of this software
 
 ```
 
 
This tool will backup the selected databases from mongodb in the machine. Using a recurring cron job is recommended to take automated backups. **Older backups are automatically deleted** to ensure that the machine does not run out of space. You can control the number of backups using ```-n``` flag. 

This tools **uploads a copy of the backup to gcloud storage** once in every 24 hours. You can change this time period using ```-c``` flag.
 
 You can also include redis backup by including ```-r``` flag.
 
