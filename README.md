# LogAgent

LogAgent is demaon program implemented in Java to upload running log files of programs (defined by regex) to upload on fixed interval to cloud storage (supported File storage - AWS, GCP, Azure).
Note : It is still currently in early stages of implementation and looking for developers.

Usage with Settings File
-----

For a very simple client setup with a settings file you first need a JSON file such as:

```
{
  "tmpDir" : "C:\\Users\\arora\\temp",
  "delayBetweenCycles": 10,
  "retentionPeriod": 3600,
  "recoveryPath": "C:\\Users\\arora\\temp\\recovery.json",
  "logFileConfigs": [
    {
    "localDirectory": "C:\\Users\\arora\\workplace\\log_agent\\LogAgent",
    "includes": [
      ".*\\.log\\..*"
    ],
    "excludes": [],
   },
  {
    "localDirectory" : "C:\\Users\\arora\\workplace\\log_agent\\LogAgent",
    "includes": [".*\\.log"],
    "excludes": []
  }
  ]
}
```

where:

* `tmpDir` - is the name of the temp directory where the LogAgent would store temp files such as gzip files.
* `delayBetweenCycles` - delay between the publishing cycle. This is the interval at which LogAgent would upload logs.
* `retentionPeriod` - defines the period after which log file would be deleted and no longer published to the endpoints.
* `logFileConfigs` - list of the configs that define the path and regex expression for the file to upload.
