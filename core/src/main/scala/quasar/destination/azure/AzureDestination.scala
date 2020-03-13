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

import quasar.api.destination.DestinationType
import quasar.api.resource.ResourcePath
import quasar.blobstore.azure.{AzurePutService, Expires}
import quasar.blobstore.paths.{BlobPath, PathElem, Path}
import quasar.blobstore.services.PutService
import quasar.connector.destination.{ResultSink, UntypedDestination}
import quasar.connector.render.RenderConfig

import cats.data.NonEmptyList
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._

import com.microsoft.azure.storage.blob.ContainerURL

import eu.timepit.refined.auto._

import fs2.Stream

final case class AzureDestination[F[_]: ConcurrentEffect: ContextShift: Timer](
    refContainerURL: Ref[F, Expires[ContainerURL]],
    refresh: F[Unit])
    extends UntypedDestination[F] {

  def destinationType: DestinationType = DestinationType("azure-dest", 1L)

  def sinks: NonEmptyList[ResultSink[F, Unit]] = NonEmptyList.one(csvSink)

  private def csvSink = ResultSink.create[F, Unit](RenderConfig.Csv()) {
    case (path, _, bytes) => Stream.eval(for {
      _ <- refresh
      containerURL <- refContainerURL.get
      put: PutService[F] = AzurePutService.mk[F](containerURL.value)
      _ <- put((toBlobPath(path), bytes))
    } yield ())
  }

  private def toBlobPath(path: ResourcePath): BlobPath =
    BlobPath(toPath(path))

  private def toPath(path: ResourcePath): Path =
    ResourcePath.resourceNamesIso.get(path).map(n => PathElem(n.value)).toList
}
