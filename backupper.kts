#!/bin/bash

//usr/bin/env echo '
/**** BOOTSTRAP kscript ****\'>/dev/null
command -v kscript >/dev/null 2>&1 || curl -L "https://git.io/fpF1K" | bash 1>&2
exec kscript $0 "$@"
\*** IMPORTANT: Any code including imports and annotations must come after this line ***/
@file:DependsOn("com.github.holgerbrandl:kutils:0.12")

import org.apache.commons.cli.*
import java.io.File
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

//DEPS commons-cli:commons-cli:20040117.000000,log4j:log4j:1.2.14

/**
 * Goal of the program:
 *        1. Create 2 days of rolling offline backups taken every 1 hours on the device
 *        2. Upload 24 hours backup of the db contents through gsutil bucket
 */

val PROG_NAME = "backupper"
val VERSION = "- 0.1.0-SNAPSHOT"
val HOME = System.getenv("HOME") ?: "/tmp/"
val NUMBER_OF_BACKUP_TO_RETAIN = System.getenv("NUMBER_OF_BACKUP_TO_RETAIN") ?: "20"
val CLOUD_STORAGE_UPLOAD_PERIOD_IN_HOURS = System.getenv("CLOUD_STORAGE_UPLOAD_PERIOD_IN_HOURS") ?: "24"
var LOG_VERBOSE = (System.getenv("LOG_VERBOSE") ?: "false").toBoolean()
val CLOUD_STORAGE_BUCKET_NAME = System.getenv("CLOUD_STORAGE_BUCKET_NAME")
val HOST_NAME = System.getenv("HOSTNAME") ?: InetAddress.getLocalHost().hostName


main()

fun main() {
    val (options, parsed) = createCommandLineParser()!!
    LOG_VERBOSE = LOG_VERBOSE || parsed.getOptionValue("l").toBoolean()
    handleCommand(options, parsed)
}

class AnsiColors {
    companion object {
        const val ANSI_RESET = "\u001B[0m";
        const val ANSI_RED = "\u001B[31m";
        const val ANSI_GREEN = "\u001B[32m";
        const val ANSI_YELLOW = "\u001B[33m"
        const val ANSI_BLUE = "\u001B[34m"
        const val ANSI_PURPLE = "\u001B[35m"
        const val ANSI_CYAN = "\u001B[36m"
        const val ANSI_WHITE = "\u001B[37m"
    }
}
fun logInfo(message: String) {
    if (LOG_VERBOSE) {
        println("${Instant.now(Clock.systemDefaultZone())}: ${AnsiColors.ANSI_BLUE}$message${AnsiColors.ANSI_RESET}")
    }
}
fun logSuccess(message: String) = println("${Instant.now(Clock.systemDefaultZone())}: ${AnsiColors.ANSI_GREEN}$message${AnsiColors.ANSI_RESET}")
fun logWarn(message: String) = println("${Instant.now(Clock.systemDefaultZone())}: ${AnsiColors.ANSI_YELLOW}$message${AnsiColors.ANSI_RESET}")
fun logError(message: String) = println("${Instant.now(Clock.systemDefaultZone())}: ${AnsiColors.ANSI_RED}$message${AnsiColors.ANSI_RESET}")

@Throws(CommandExecutionException::class)
fun String.exec(dir: File? = null): Int {
    logWarn("executing: $this")
    val processBuilder = ProcessBuilder("/bin/sh", "-c", this)
        .redirectErrorStream(true)
        .inheritIO()
        .directory(dir)
    val exitValue = processBuilder.start()
        .waitFor()
    if (exitValue != 0) {
        throw CommandExecutionException()
    }
    return exitValue
}

@Throws(CommandExecutionException::class)
fun backupMongoDb(db: String, numberOfBackupsOnMachine: Int, cloudStorageUploadPeriodInHours: Int, bucketName: String) {
    val STORAGE_PATH="$bucketName/$HOST_NAME/mongodb/$db/"
    val instant = Instant.now(Clock.systemDefaultZone())
    val BACKUP_NAME= instant.toString()
    logInfo("Date: $instant Home:$HOME")
    "mkdir -p $HOME/.backupper/archives/$db/".exec()
    val targetFileName = "$HOME/.backupper/archives/$db/$BACKUP_NAME.gz"
    logInfo("Dumping MongoDB $db database to compressed archive $targetFileName")
    "mongodump --db $db --archive=$targetFileName --gzip".exec()
    logSuccess("Exported MongoDB $db database to compressed archive")
    val ledgerFile = File("$HOME/.backupper/logs/$db.ledger")
    ledgerFile.parentFile.mkdirs()
    ledgerFile.appendText("${States.BACKED_UP.name};$targetFileName\n")
    checkForUploadToGcloudStorage(ledgerFile,
        targetFileName,
        STORAGE_PATH,
        BACKUP_NAME,
        cloudStorageUploadPeriodInHours)
    cleanUp(db, ledgerFile, numberOfBackupsOnMachine)
}

enum class States {
    UPLOADED, BACKED_UP, DELETED_BACKUP, BACKING_UP_ERROR, UPLOADING_ERROR
}

fun checkForUploadToGcloudStorage(
    ledgerFile: File,
    exportedFilePath: String,
    storagePath: String,
    backupName: String,
    cloudStorageUploadPeriodInHours: Int
) {
    val line = ledgerFile.readLines().lastOrNull { it.contains(States.UPLOADED.name) } ?: Instant.ofEpochMilli(0).toString()
    logWarn("lastUploaded $line")
    val lastUploadedInstant = Instant.parse(line.replace("${States.UPLOADED};", ""))
    if (lastUploadedInstant.isBefore(Instant.now(Clock.systemDefaultZone()).minus(cloudStorageUploadPeriodInHours.toLong(), ChronoUnit.HOURS))) {
        "gsutil cp $exportedFilePath gs://$storagePath/$backupName/".exec()
        logSuccess("Uploaded $exportedFilePath to $backupName on $storagePath")
        ledgerFile.appendText("${States.UPLOADED.name};${Instant.now(Clock.systemDefaultZone())}\n")
    } else {
        logWarn("Skipping cloud storage upload")
    }
}

fun cleanUp(archDirName: String, ledgerFile: File, numberOfBackupsOnMachine: Int) {
    val dirPath = "$HOME/.backupper/archives/$archDirName/"
    val dir = File(dirPath)
    val files = dir.listFiles { it -> it.name.endsWith(".gz") || it.name.endsWith(".rdb") }
    if ((files?.size ?:0) > numberOfBackupsOnMachine) {
        files!!.sortBy { it.name}
        val toBeDeleted = files.take(files.size - numberOfBackupsOnMachine)
        try {
            toBeDeleted.forEach {
                it.deleteRecursively()
                ledgerFile.appendText("${States.DELETED_BACKUP};${it.absolutePath}\n")
                logSuccess("Deleted ${it.absolutePath}")
            }
        } catch (e: Throwable) {
            logWarn("Delete error ${e.stackTraceToString()}")
        }
    }
}

fun createCommandLineParser(): Pair<Options, CommandLine>? {
    val options = Options()
    with(options) {
        addOption(Option("h", "help", false, "Prints this help message"))
        addOption(Option("v", "version", false, "Prints the version of this software"))
        addOption(Option("l", "verbose", false, "Turn on verbose logging"))
        addOption(Option("b", "bucket-name", true, "Storage bucket to use").also { it.isRequired = true })
        addOption(Option("d", "databases", true, "Database name to backup").also {
            it.valueSeparator = ','
            it.argName = "db1,db2"
            it.isRequired = true
        })
        addOption(
            Option("n", "numberOfBackupsOnMachine", true,
                "Number of backups to be maintained on the machine. \nOlder backups will be deleted while retaining only this number of backups.\nDefault values = 20"
            ).apply {
                isRequired = false
            })
        addOption(Option("r", "redis", false, "Enable Redis rdb backup"))
        addOption(Option("c", "cloudUploadPeriodInHours", false, "Cloud Upload Period in hours. The time period between cloud backups.\nDefault Value = 24"))
    }
    val parser = PosixParser()
    return try {
        val parsed: CommandLine = parser.parse(options, args)
        Pair(options, parsed)
    } catch (pe: ParseException) {
        val formatter = HelpFormatter()
        formatter.printHelp("backupper",options)
        exitProcess(-1)
    }
}

fun handleCommand(options: Options, parsed: CommandLine) {
    if (LOG_VERBOSE) {
        parsed.options.forEach { logInfo("${it.longOpt} - ${it.value}") }
    }
    if (parsed.hasOption("h")) {
        val formatter = HelpFormatter()
        formatter.printHelp("backupper", options)
        return
    }

    if (parsed.hasOption("v")) {
        println("$PROG_NAME $VERSION")
    }

    val numberOfBackupsOnMachine = (parsed.getOptionValue("n") ?: NUMBER_OF_BACKUP_TO_RETAIN).toInt()
    val bucketName = (parsed.getOptionValue("b") ?: CLOUD_STORAGE_BUCKET_NAME) ?: throw IllegalArgumentException("Bucket Name not specified")
    val cloudStorageUploadPeriodInHours = (parsed.getOptionValue("c") ?: CLOUD_STORAGE_UPLOAD_PERIOD_IN_HOURS).toInt()
    val dbs = (parsed.getOptionValue("d") ?: "").split(",")




    try {
        if (dbs.isNotEmpty()) {
            dbs.forEach {
                backupMongoDb(it, numberOfBackupsOnMachine, cloudStorageUploadPeriodInHours, bucketName)
            }
        } else {
            logInfo("No database name passed to backup")
        }
    } catch (e: CommandExecutionException) {
        logError("Failed!")
    }

    if (parsed.hasOption("r")) {
        try {
            val backupName = Instant.now(Clock.systemDefaultZone())
            val exportFileName = "$HOME/.backupper/archives/_redis_/$backupName.rdb"
            File(exportFileName).parentFile.mkdirs()
            "redis-cli --rdb $exportFileName".exec()
            val ledgerFile = File("$HOME/.backupper/logs/_redis_.ledger")
            ledgerFile.appendText("${States.BACKED_UP};$exportFileName\n")
            val storagePath="$bucketName/$HOST_NAME/redis/"
            checkForUploadToGcloudStorage(ledgerFile,
                exportFileName,
                storagePath,
                backupName.toString(),
                cloudStorageUploadPeriodInHours)
            cleanUp("_redis_", ledgerFile, numberOfBackupsOnMachine)
        } catch (e: CommandExecutionException) {
            logError("Failed!")
        }

    }
}

fun CommandLine.getValue(s: String): String? {
    val v = getOptionValue(s)
    logInfo("option $s: Value = $v");
    return v
}


class CommandExecutionException: Exception()