/*
 * Copyright 2014–2020 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.destination.azure

import quasar.blobstore.azure.{
  AccountKey,
  AccountName,
  AzureCredentials,
  ContainerName,
  StorageUrl
}

import argonaut._, Argonaut._
import org.specs2.mutable.Specification

object AzureConfigSpec extends Specification {
  "parses a valid configuration" >> {
    val testConfig = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "sharedKey",
        "accountName" := "some-name",
        "accountKey" := "some-key"))

    testConfig.as[AzureConfig].result must beRight(AzureConfig(
      ContainerName("some-name"),
      StorageUrl("https://some-name.blob.core.windows.net"),
      AzureCredentials.SharedKey(
        AccountName("some-name"),
        AccountKey("some-key"))))
  }

  "defaults to shared key authentication when not specified" >> {
    val testConfig = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "accountName" := "some-name",
        "accountKey" := "some-key"))

    testConfig.as[AzureConfig].result must beRight(AzureConfig(
      ContainerName("some-name"),
      StorageUrl("https://some-name.blob.core.windows.net"),
      AzureCredentials.SharedKey(
        AccountName("some-name"),
        AccountKey("some-key"))))
  }

  "rejects unknown 'auth' values" >> {
    val testConfig = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "nope",
        "accountName" := "some-name",
        "accountKey" := "some-key"))

    testConfig.as[AzureConfig].result must beLeft
  }

  "credentials are mandatory" >> {
    val testConfig = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net")

    testConfig.as[AzureConfig].result must beLeft
  }
}
