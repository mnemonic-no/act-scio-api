# scio-api

Web API for submitting and retrieving documents from Scio

## Build

```
$ lein deps
$ lein uberjar
```

## Usage

```
$ java -jar target/scio-api-0.1.0-SNAPSHOT-standalone.jar -h
Usage: java -jar scio-api-VERSION.jar [OPTION...]

-c, --config=CONFIGFILE      Specify ini location
-h, --help                   print this text

You can also override the default location of the .ini file by exporting the SCIOAPIINI environment variable.

report bugs to opensource@mnemonic.no
```

*examples*
```
$ java -jar scio-api-0.1.0-standalone.jar -c /opt/scio/scio.ini
```

```
$ SCIOAPIINI=/opt/scio/scio.ini java -jar scio-api-0.1.0-standalone.jar

## Configuration

*Consider sharing the .ini file between scio and scio-api*

```
[beanstalk]
queue = submit
host = localhost
port = 11300

[elasticsearch]
host = http://localhost:9201 http://localhost:9202

[storage]
storagedir = /tmp

[api]
port = 3000
```

### [beanstalk]

*queue*: This is the beanstalk tube shared by scio-api and scio. This is where scio retrieves the filenames
to scrape for indicators.

*host*: The hostname of the beanstalkd service

*port*: The ports running the beanstalkd service

### [elasticsearch]
*host*: A list of hosts separated by space. This is the list of *coordinators* in the case of a cluster.

### [storagedir]

*storagedir*: The directory where the submitted documents are stored. This can be the same place as scio stores the documents. *Important: scio must be able to read the files in this directory*

### [api]
*port*: The port the web API will be listening on.

## Tests using curl to verify everything works

### Submitting data

The Content-Type *MUST* be "application/json"
The data must be a json map with the keys "content" containing the file content as base64 and "filename" containing the basename of the file.

```
$ curl -H "Content-Type: application/json" -X POST --data '{"content": "MTI3LjAuMC4xCg==", "filename": "testfile.txt"}' http://localhost:3000/submit
ok⏎
$
```

### Downloading data

```
$ curl 'http://localhost:3000?id=77785fc7b151a325a39d2c40a7701cb57736f2ce34b7edbef4e538f42c1509d3'
{"filename":"testfile.txt","content":"MTI3LjAuMC4xCg==","encoding":"base64"}
$
```

## License

ISC License

Copyright (c) 2016-2018, mnemonic as <opensource@mnemonic.no>

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE
OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
