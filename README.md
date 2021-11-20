# backupper

## Installation


### Install Dependencies

Tested with the following dependencies but it should work for java 8+


- Openjdk version "11.0.12" 2021-07-20 LTS
- Kotlin  version 1.5.31 (JRE 11.0.12+7-LTS)
- KScript version 3.1.0

Using [sdkman](https://sdkman.io/install) 

```
sdk install java 11.0.12-zulu
sdk install kotlin
sdk install kscript
```
### Install backupper

```
wget https://raw.githubusercontent.com/Gopinathp/backupper/main/backupper.kts
chmod u+x backupper.kts
./backupper.kts
```

## Usage
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
 
