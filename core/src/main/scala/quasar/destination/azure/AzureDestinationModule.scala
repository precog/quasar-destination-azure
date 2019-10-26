/*
 * Copyright 2014–2019 SlamData Inc.
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

import scala._
import scala.Predef._
import scala.util.Either

import quasar.api.destination.DestinationError.InitializationError
import quasar.api.destination.{Destination, DestinationType}
import quasar.api.destination.{DestinationError, DestinationType}
import quasar.connector.{DestinationModule, MonadResourceErr}
import quasar.blobstore.azure.{
  AccountKey,
  AccountName,
  Azure,
  AzureCredentials,
  AzureGetService,
  AzurePutService,
  AzureStatusService
}
import quasar.blobstore.BlobstoreStatus

import argonaut._, Argonaut._

import eu.timepit.refined.auto._

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.instances.either._
import cats.syntax.bifunctor._
import cats.syntax.either._
import com.microsoft.azure.storage.blob.ContainerURL
import scalaz.NonEmptyList

object AzureDestinationModule extends DestinationModule {
  private val Redacted = "<REDACTED>"
  private val RedactedCreds =
    AzureCredentials(AccountName(Redacted), AccountKey(Redacted))

  def destinationType: DestinationType =
    DestinationType("azure-dest", 1L)

  def sanitizeDestinationConfig(config: Json): Json =
    config.as[AzureConfig].result.fold(_ => Json.jEmptyObject, cfg =>
      cfg.copy(credentials = RedactedCreds).asJson)

  def destination[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer](
    config: Json): Resource[F, Either[InitializationError[Json], Destination[F]]] = {

    val dest = for {
      azureConfig <- EitherT.fromEither[F][InitializationError[Json], AzureConfig](
        config.as[AzureConfig].result.leftMap {
          case (err, _) =>
            DestinationError.malformedConfiguration((destinationType, config, err))
        })

      containerUrl <- EitherT.liftF(Azure.mkContainerUrl[F](AzureConfig.toConfig(azureConfig)))
      status <- EitherT.liftF(AzureStatusService.mk[F](containerUrl))

      _ <- EitherT.fromEither[F](status match {
        case BlobstoreStatus.NotFound =>
          DestinationError.invalidConfiguration((
            destinationType, config, NonEmptyList("Container not found"))).asLeft
        case BlobstoreStatus.NoAccess =>
          DestinationError.accessDenied((
            destinationType, config, "Access denied")).asLeft
        case BlobstoreStatus.NotOk(msg) =>
          DestinationError.invalidConfiguration((
            destinationType, config, NonEmptyList(s"Unable to connect: $msg"))).asLeft
        case BlobstoreStatus.Ok =>
          ().asRight
      })

      destination = AzureDestination[F](AzurePutService.mk[F](containerUrl))

    } yield destination

    ???
  }
}
