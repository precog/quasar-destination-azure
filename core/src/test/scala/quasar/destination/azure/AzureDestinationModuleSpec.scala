/*
 * Copyright 2020 Precog Data
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

import argonaut._, Argonaut._
import org.specs2.mutable.Specification

object AzureDestinationModuleSpec extends Specification {
  "redacts shared key credentials" >> {
    val toRedact = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "sharedKey",
        "accountName" := "some-name",
        "accountKey" := "some-key"))

    val redacted = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "sharedKey",
        "accountName" := "<REDACTED>",
        "accountKey" := "<REDACTED>"))

    AzureDestinationModule
      .sanitizeDestinationConfig(toRedact) must_== redacted
  }

  "redacts Active Directory credentials" >> {
    val toRedact = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "activeDirectory",
        "clientId" := "some-client-id",
        "tenantId" := "some-tenant-id",
        "clientSecret" := "some-client-secret"))

    val redacted = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "activeDirectory",
        "clientId" := "<REDACTED>",
        "tenantId" := "<REDACTED>",
        "clientSecret" := "<REDACTED>"))

    AzureDestinationModule
      .sanitizeDestinationConfig(toRedact) must_== redacted
  }

  "redacts credentials with an unspecified 'auth'" >> {
    val toRedact = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "accountName" := "some-name",
        "accountKey" := "some-key"))

    val redacted = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "sharedKey",
        "accountName" := "<REDACTED>",
        "accountKey" := "<REDACTED>"))

    AzureDestinationModule
      .sanitizeDestinationConfig(toRedact) must_== redacted
  }

  "returns empty object when configuration parsing fails" >> {
    val toRedact = Json.obj(
      "container" := "some-name",
      "storageUrl" := "https://some-name.blob.core.windows.net",
      "credentials" := Json.obj(
        "auth" := "sharedKey",
        "name" := "some-name",
        "key" := "some-key"))

    AzureDestinationModule
      .sanitizeDestinationConfig(toRedact) must_== Json.jEmptyObject
  }
}
