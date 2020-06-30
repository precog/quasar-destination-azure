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

import scala._
import scala.util.Either

import quasar.api.destination.{DestinationError, DestinationType}
import quasar.api.destination.DestinationError.InitializationError
import quasar.connector.MonadResourceErr
import quasar.connector.destination.{Destination, DestinationModule, PushmiPullyu}
import quasar.blobstore.azure.{
  AccountKey,
  AccountName,
  Azure,
  AzureCredentials,
  AzureStatusService,
  ClientId,
  ClientSecret,
  TenantId
}
import quasar.blobstore.BlobstoreStatus

import argonaut._, Argonaut._

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._

import scalaz.NonEmptyList

object AzureDestinationModule extends DestinationModule {
  private val Redacted = "<REDACTED>"
  private val SharedKeyRedactedCreds =
    AzureCredentials.SharedKey(AccountName(Redacted), AccountKey(Redacted))
  private val ActiveDirectoryRedactedCreds =
    AzureCredentials.ActiveDirectory(ClientId(Redacted), TenantId(Redacted), ClientSecret(Redacted))

  def destinationType: DestinationType =
    DestinationType("azure-dest", 1L)

  def sanitizeDestinationConfig(config: Json): Json =
    config.as[AzureConfig].result.fold(_ => Json.jEmptyObject, cfg =>
      (cfg.credentials match {
        case AzureCredentials.SharedKey(_, _) =>
          cfg.copy(credentials = SharedKeyRedactedCreds)
        case AzureCredentials.ActiveDirectory(_, _, _) =>
          cfg.copy(credentials = ActiveDirectoryRedactedCreds)
      }).asJson)

  def destination[F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer](
      config: Json,
      pushPull: PushmiPullyu[F])
      : Resource[F, Either[InitializationError[Json], Destination[F]]] =
    Resource.liftF(
      (for {
        azureConfig <- EitherT.fromEither[F](
          config.as[AzureConfig].result.leftMap {
            case (err, _) =>
              DestinationError.malformedConfiguration((destinationType, config, err))
          })

        (refContainerClient, refresh) <- EitherT.liftF(Azure.refContainerClient(AzureConfig.toConfig(azureConfig)))
        containerClient <- EitherT.liftF(refContainerClient.get)

        status <- EitherT.liftF(AzureStatusService.mk[F](containerClient.value))

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

        destination: Destination[F] = AzureDestination[F](refContainerClient, refresh)

      } yield destination).value)
}
