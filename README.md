# quasar-destination-azure [![Build Status](https://travis-ci.com/slamdata/quasar-destination-azure.svg?branch=master)](https://travis-ci.com/slamdata/quasar-destination-azure) [![Bintray](https://img.shields.io/bintray/v/slamdata-inc/maven-public/quasar-destination-azure.svg)](https://bintray.com/slamdata-inc/maven-public/quasar-destination-azure) [![Discord](https://img.shields.io/discord/373302030460125185.svg?logo=discord)](https://discord.gg/QNjwCg6)

## Usage

```sbt
libraryDependencies += "com.slamdata" %% "quasar-destination-azure" % <version>
```

## Configuration

```json
{
  "container": String,
  "storageUrl": String,
  "credentials": {
    "accountName": String,
    "accountKey": String
  }
}
```

* `container` the name of the Azure blobstore container to use.
* `storageUrl` the Azure storage URL to use. Typically this will be an URL of the form `https://<accountName>.blob.core.windows.net/`.
* `credentials` (mandatory) Azure credentials to use for access.
